package com.campus.complaints;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SlaDashboardService {

    private static final List<ComplaintStatus> TERMINAL_STATUSES =
            List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

    private final ComplaintRepository complaintRepository;
    private final ComplaintAssignmentRepository assignmentRepository;
    private final ComplaintSlaEscalationRepository escalationRepository;

    public SlaDashboardService(
            ComplaintRepository complaintRepository,
            ComplaintAssignmentRepository assignmentRepository,
            ComplaintSlaEscalationRepository escalationRepository) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.escalationRepository = escalationRepository;
    }

    @Transactional(readOnly = true)
    public SlaDashboardResponse dashboard(
            AuthenticatedUser principal,
            SlaFilter status,
            ComplaintPriority priority,
            Integer escalationLevel,
            Long assignedStaffId,
            Integer minimumOverdueHours,
            LocalDate deadlineFrom,
            LocalDate deadlineTo,
            int page,
            int size) {
        requireAdmin(principal);
        if (escalationLevel != null && (escalationLevel < 0 || escalationLevel > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalation level must be between 0 and 3.");
        }
        if (minimumOverdueHours != null && minimumOverdueHours < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overdue duration cannot be negative.");
        }
        if (deadlineFrom != null && deadlineTo != null && deadlineFrom.isAfter(deadlineTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline start must not follow deadline end.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Specification<Complaint> tableFilter = filtered(status, priority, escalationLevel, assignedStaffId,
                minimumOverdueHours, deadlineFrom, deadlineTo, now);
        Page<Complaint> complaints = complaintRepository.findAll(
                tableFilter,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("resolutionDeadlineAt"), Sort.Order.asc("id"))));
        Map<Long, List<String>> activeAssignees = activeAssigneeNames(complaints.getContent());
        List<Long> complaintIds = complaints.getContent().stream().map(Complaint::getId).toList();
        Map<Long, List<SlaDashboardResponse.EscalationHistory>> histories =
                complaintIds.isEmpty() ? Map.of() : escalationHistory(complaintIds);
        List<SlaDashboardResponse.SlaComplaintRow> rows = complaints.getContent().stream()
                .map(complaint -> {
                    SlaAssessment assessment = SlaAssessment.assess(complaint, now);
                    return new SlaDashboardResponse.SlaComplaintRow(
                            complaint.getId(),
                            complaint.getComplaintCode(),
                            complaint.getTitle(),
                            complaint.getPriority(),
                            String.join(", ", activeAssignees.getOrDefault(complaint.getId(), List.of())),
                            complaint.getResolutionDeadlineAt(),
                            assessment.secondsUntilDeadline(),
                            assessment.status(),
                            complaint.getEscalationLevel(),
                            histories.getOrDefault(complaint.getId(), List.of()));
                })
                .toList();

        return new SlaDashboardResponse(
                count(SlaFilter.ON_TRACK, now),
                count(SlaFilter.DUE_SOON, now),
                count(SlaFilter.OVERDUE, now),
                count(SlaFilter.RESOLVED_WITHIN_SLA, now),
                count(SlaFilter.RESOLVED_AFTER_SLA, now),
                List.of(
                        new SlaDashboardResponse.EscalationCount(0,
                                complaintRepository.count(filteredEscalationLevel(0))),
                        new SlaDashboardResponse.EscalationCount(1,
                                complaintRepository.count(filteredEscalationLevel(1))),
                        new SlaDashboardResponse.EscalationCount(2,
                                complaintRepository.count(filteredEscalationLevel(2))),
                        new SlaDashboardResponse.EscalationCount(3,
                                complaintRepository.count(filteredEscalationLevel(3)))),
                new PageImpl<>(rows, complaints.getPageable(), complaints.getTotalElements()));
    }

    private long count(SlaFilter status, LocalDateTime now) {
        return complaintRepository.count(filtered(status, null, null, null, null, null, null, now));
    }

    private Specification<Complaint> filtered(
            SlaFilter status,
            ComplaintPriority priority,
            Integer escalationLevel,
            Long assignedStaffId,
            Integer minimumOverdueHours,
            LocalDate deadlineFrom,
            LocalDate deadlineTo,
            LocalDateTime now) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(statusPredicate(status, root, query, builder, now));
            }
            if (priority != null) {
                predicates.add(builder.equal(root.get("priority"), priority));
            }
            if (escalationLevel != null) {
                predicates.add(builder.equal(root.get("escalationLevel"), escalationLevel));
            }
            if (assignedStaffId != null) {
                jakarta.persistence.criteria.Subquery<Long> assignmentQuery = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<ComplaintAssignment> assignment =
                        assignmentQuery.from(ComplaintAssignment.class);
                assignmentQuery.select(assignment.get("complaint").get("id"))
                        .where(builder.equal(assignment.get("assignee").get("id"), assignedStaffId),
                                builder.isNull(assignment.get("unassignedAt")));
                predicates.add(root.get("id").in(assignmentQuery));
            }
            if (minimumOverdueHours != null) {
                predicates.add(unresolved(root, builder));
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("resolutionDeadlineAt"), now.minusHours(minimumOverdueHours)));
            }
            if (deadlineFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("resolutionDeadlineAt"), deadlineFrom.atStartOfDay()));
            }
            if (deadlineTo != null) {
                predicates.add(builder.lessThan(
                        root.get("resolutionDeadlineAt"), deadlineTo.plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate statusPredicate(
            SlaFilter status,
            jakarta.persistence.criteria.Root<Complaint> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            LocalDateTime now) {
        return switch (status) {
            case ON_TRACK -> builder.and(
                    unresolved(root, builder),
                    builder.greaterThan(root.get("slaDueSoonAt"), now));
            case DUE_SOON -> builder.and(
                    unresolved(root, builder),
                    builder.lessThanOrEqualTo(root.get("slaDueSoonAt"), now),
                    builder.greaterThan(root.get("resolutionDeadlineAt"), now));
            case OVERDUE -> builder.and(
                    unresolved(root, builder),
                    builder.lessThanOrEqualTo(root.get("resolutionDeadlineAt"), now));
            case RESOLVED_WITHIN_SLA -> builder.and(
                    builder.isNotNull(root.get("resolvedAt")),
                    builder.lessThanOrEqualTo(root.get("resolvedAt"), root.get("resolutionDeadlineAt")));
            case RESOLVED_AFTER_SLA -> builder.and(
                    builder.isNotNull(root.get("resolvedAt")),
                    builder.greaterThan(root.get("resolvedAt"), root.get("resolutionDeadlineAt")));
        };
    }

    private Predicate unresolved(
            jakarta.persistence.criteria.Root<Complaint> root,
            jakarta.persistence.criteria.CriteriaBuilder builder) {
        return builder.and(
                builder.isNull(root.get("resolvedAt")),
                builder.not(root.get("status").in(TERMINAL_STATUSES)));
    }

    private Specification<Complaint> filteredEscalationLevel(int escalationLevel) {
        return (root, query, builder) -> builder.equal(root.get("escalationLevel"), escalationLevel);
    }

    private Map<Long, List<String>> activeAssigneeNames(List<Complaint> complaints) {
        if (complaints.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = complaints.stream().map(Complaint::getId).toList();
        return assignmentRepository.findByComplaint_IdInAndUnassignedAtIsNull(ids).stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getComplaint().getId(),
                        Collectors.mapping(
                                assignment -> assignment.getAssignee().getFullName(),
                                Collectors.toList())));
    }

    private Map<Long, List<SlaDashboardResponse.EscalationHistory>> escalationHistory(List<Long> complaintIds) {
        return escalationRepository.findByComplaint_IdInOrderByEscalatedAtAscIdAsc(complaintIds).stream()
                .collect(Collectors.groupingBy(
                        escalation -> escalation.getComplaint().getId(),
                        Collectors.mapping(
                                escalation -> new SlaDashboardResponse.EscalationHistory(
                                        escalation.getEscalationLevel(),
                                        escalation.getEscalatedAt(),
                                        escalation.getReason()),
                                Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            AuthenticatedUser principal,
            SlaFilter status,
            ComplaintPriority priority,
            Integer escalationLevel,
            Long assignedStaffId,
            Integer minimumOverdueHours,
            LocalDate deadlineFrom,
            LocalDate deadlineTo) {
        requireAdmin(principal);
        if (escalationLevel != null && (escalationLevel < 0 || escalationLevel > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalation level must be between 0 and 3.");
        }
        if (minimumOverdueHours != null && minimumOverdueHours < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overdue duration cannot be negative.");
        }
        if (deadlineFrom != null && deadlineTo != null && deadlineFrom.isAfter(deadlineTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deadline start must not follow deadline end.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Specification<Complaint> tableFilter = filtered(status, priority, escalationLevel, assignedStaffId,
                minimumOverdueHours, deadlineFrom, deadlineTo, now);
        List<Complaint> complaints = complaintRepository.findAll(
                tableFilter,
                Sort.by(Sort.Order.asc("resolutionDeadlineAt"), Sort.Order.asc("id")));
        Map<Long, List<String>> activeAssignees = activeAssigneeNames(complaints);

        StringBuilder csv = new StringBuilder(
                "Complaint ID,Title,Priority,Status,Assigned Staff,Submitted At,SLA Target Hours,Due Soon At,Resolution Deadline,Resolved At,SLA Status,Overdue Hours,Escalation Level,Last Escalated At\r\n");
        for (Complaint complaint : complaints) {
            SlaAssessment assessment = SlaAssessment.assess(complaint, now);
            List<String> assignees = activeAssignees.getOrDefault(complaint.getId(), List.of());
            String assignedStaff = assignees.isEmpty() ? "Unassigned" : String.join("; ", assignees);
            csv.append(escapeCsv(complaint.getComplaintCode())).append(',')
                    .append(escapeCsv(complaint.getTitle())).append(',')
                    .append(escapeCsv(complaint.getPriority().name())).append(',')
                    .append(escapeCsv(complaint.getStatus().name())).append(',')
                    .append(escapeCsv(assignedStaff)).append(',')
                    .append(escapeCsv(complaint.getCreatedAt().toString())).append(',')
                    .append(complaint.getSlaTargetHours()).append(',')
                    .append(escapeCsv(complaint.getSlaDueSoonAt() != null ? complaint.getSlaDueSoonAt().toString() : "")).append(',')
                    .append(escapeCsv(complaint.getResolutionDeadlineAt() != null ? complaint.getResolutionDeadlineAt().toString() : "")).append(',')
                    .append(escapeCsv(complaint.getResolvedAt() != null ? complaint.getResolvedAt().toString() : "")).append(',')
                    .append(escapeCsv(assessment.status().name())).append(',')
                    .append(assessment.secondsUntilDeadline() < 0 ? Math.abs(assessment.secondsUntilDeadline()) / 3600 : 0).append(',')
                    .append(complaint.getEscalationLevel()).append(',')
                    .append(escapeCsv(complaint.getLastEscalationAt() != null ? complaint.getLastEscalationAt().toString() : ""))
                    .append("\r\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-") || escaped.startsWith("@")) {
            escaped = "'" + escaped;
        }
        return "\"" + escaped + "\"";
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }
}
