package com.campus.complaints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditEntryRepository auditEntryRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.auditEntryRepository = auditEntryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            AuthenticatedUser actor,
            String actionType,
            String entityType,
            String entityId,
            String entityReference,
            Long complaintId,
            String complaintReference,
            String description,
            Map<String, ?> beforeState,
            Map<String, ?> afterState) {
        User user = userRepository.findById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalStateException("Cannot audit an unavailable actor."));
        if (user.getRole() != actor.role()) {
            throw new IllegalStateException("Cannot audit an actor whose role changed during the request.");
        }
        auditEntryRepository.saveAndFlush(new AuditEntry(
                UUID.randomUUID().toString(),
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                actionType,
                entityType,
                entityId,
                entityReference,
                complaintId,
                complaintReference,
                description,
                serialize(beforeState),
                serialize(afterState)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(
            String actionType,
            String entityType,
            String entityId,
            String entityReference,
            Long complaintId,
            String complaintReference,
            String description,
            Map<String, ?> beforeState,
            Map<String, ?> afterState) {
        auditEntryRepository.saveAndFlush(new AuditEntry(
                UUID.randomUUID().toString(),
                null,
                "CampusCare SLA Scheduler",
                "SYSTEM",
                actionType,
                entityType,
                entityId,
                entityReference,
                complaintId,
                complaintReference,
                description,
                serialize(beforeState),
                serialize(afterState)));
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> search(
            int page,
            int size,
            String search,
            String actionType,
            String actorRole,
            Long actorUserId,
            LocalDate from,
            LocalDate to) {
        Specification<AuditEntry> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("actorName")), pattern),
                        builder.like(builder.lower(root.get("actionType")), pattern),
                        builder.like(builder.lower(root.get("entityType")), pattern),
                        builder.like(builder.lower(root.get("entityReference")), pattern),
                        builder.like(builder.lower(root.get("complaintReference")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(builder.equal(root.get("actionType"), actionType));
            }
            if (actorRole != null && !actorRole.isBlank()) {
                predicates.add(builder.equal(root.get("actorRole"), actorRole));
            }
            if (actorUserId != null) {
                predicates.add(builder.equal(root.get("actorUserId"), actorUserId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        return auditEntryRepository.findAll(specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))))
                .map(AuditResponse::from);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            String search,
            String actionType,
            String actorRole,
            Long actorUserId,
            LocalDate from,
            LocalDate to) {
        Specification<AuditEntry> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("actorName")), pattern),
                        builder.like(builder.lower(root.get("actionType")), pattern),
                        builder.like(builder.lower(root.get("entityType")), pattern),
                        builder.like(builder.lower(root.get("entityReference")), pattern),
                        builder.like(builder.lower(root.get("complaintReference")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(builder.equal(root.get("actionType"), actionType));
            }
            if (actorRole != null && !actorRole.isBlank()) {
                predicates.add(builder.equal(root.get("actorRole"), actorRole));
            }
            if (actorUserId != null) {
                predicates.add(builder.equal(root.get("actorUserId"), actorUserId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        List<AuditEntry> entries = auditEntryRepository.findAll(
                specification,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));

        StringBuilder sb = new StringBuilder();
        sb.append("Audit ID,Timestamp,Actor Name,Actor Role,Action Type,Entity Type,Entity Reference,Complaint Reference,Description\n");
        for (AuditEntry entry : entries) {
            sb.append(escape(entry.getEventKey())).append(",")
                    .append(escape(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : "")).append(",")
                    .append(escape(entry.getActorName())).append(",")
                    .append(escape(entry.getActorRole())).append(",")
                    .append(escape(entry.getActionType())).append(",")
                    .append(escape(entry.getEntityType())).append(",")
                    .append(escape(entry.getEntityReference())).append(",")
                    .append(escape(entry.getComplaintReference())).append(",")
                    .append(escape(entry.getDescription())).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        String clean = value.replace("\r", " ").replace("\n", " ");
        if (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@")) {
            clean = "'" + clean;
        }
        return "\"" + clean.replace("\"", "\"\"") + "\"";
    }

    private String serialize(Map<String, ?> state) {
        if (state == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit state could not be serialized.", exception);
        }
    }
}
