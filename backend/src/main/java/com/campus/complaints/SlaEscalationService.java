package com.campus.complaints;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class SlaEscalationService {

    private static final List<ComplaintStatus> TERMINAL_STATUSES =
            List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

    private final ComplaintRepository complaintRepository;
    private final ComplaintSlaEscalationRepository escalationRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SlaPolicy slaPolicy;

    public SlaEscalationService(
            ComplaintRepository complaintRepository,
            ComplaintSlaEscalationRepository escalationRepository,
            NotificationService notificationService,
            AuditService auditService,
            SlaPolicy slaPolicy) {
        this.complaintRepository = complaintRepository;
        this.escalationRepository = escalationRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.slaPolicy = slaPolicy;
    }

    @Scheduled(fixedDelayString = "${app.sla.escalation-check-ms:60000}")
    @Transactional
    public void processOverdueComplaints() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Process due-soon reminders
        List<Long> dueSoonIds = complaintRepository.findDueSoonUnresolvedIds(now, TERMINAL_STATUSES);
        for (Long complaintId : dueSoonIds) {
            complaintRepository.findById(complaintId).ifPresent(complaint -> {
                if (complaint.getResolvedAt() == null
                        && !TERMINAL_STATUSES.contains(complaint.getStatus())
                        && complaint.getResolutionDeadlineAt().isAfter(now)) {
                    notificationService.slaDueSoon(complaint);
                }
            });
        }

        // Process escalations
        List<Long> overdueIds = complaintRepository.findOverdueUnresolvedIds(now, TERMINAL_STATUSES);
        for (Long complaintId : overdueIds) {
            complaintRepository.findByIdForUpdate(complaintId).ifPresent(complaint -> {
                if (complaint.getResolvedAt() != null
                        || TERMINAL_STATUSES.contains(complaint.getStatus())
                        || complaint.getResolutionDeadlineAt().isAfter(now)) {
                    return;
                }
                long overdueHours = ChronoUnit.HOURS.between(complaint.getResolutionDeadlineAt(), now);
                int targetLevel = slaPolicy.escalationLevel(overdueHours);
                while (complaint.getEscalationLevel() < targetLevel) {
                    int nextLevel = complaint.getEscalationLevel() + 1;
                    if (escalationRepository.existsByComplaint_IdAndEscalationLevel(complaintId, nextLevel)) {
                        complaint.recordEscalation(nextLevel, now);
                        continue;
                    }
                    String reason = escalationReason(nextLevel);
                    ComplaintSlaEscalation escalation = escalationRepository.saveAndFlush(
                            new ComplaintSlaEscalation(complaint, nextLevel, now, reason));
                    complaint.recordEscalation(nextLevel, now);
                    notificationService.slaEscalated(complaint, nextLevel, reason);
                    auditService.recordSystem(
                            "SLA_ESCALATED",
                            "COMPLAINT_SLA_ESCALATION",
                            escalation.getId().toString(),
                            complaint.getComplaintCode(),
                            complaint.getId(),
                            complaint.getComplaintCode(),
                            "Complaint escalated to level " + nextLevel + ".",
                            Map.of("escalationLevel", nextLevel - 1),
                            Map.of("escalationLevel", nextLevel, "reason", reason));
                }
            });
        }
    }

    private String escalationReason(int level) {
        return switch (level) {
            case 1 -> "The resolution deadline has passed.";
            case 2 -> "The complaint has been overdue for at least 24 elapsed hours.";
            case 3 -> "The complaint has been overdue for at least 48 elapsed hours.";
            default -> throw new IllegalArgumentException("Unsupported SLA escalation level.");
        };
    }
}
