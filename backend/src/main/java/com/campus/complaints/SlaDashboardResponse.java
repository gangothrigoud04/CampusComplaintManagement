package com.campus.complaints;

import java.time.LocalDateTime;
import java.util.List;

public record SlaDashboardResponse(
        long withinSla,
        long dueSoon,
        long overdue,
        long resolvedWithinSla,
        long resolvedAfterSla,
        List<EscalationCount> escalations,
        org.springframework.data.domain.Page<SlaComplaintRow> complaints) {

    public record EscalationCount(int level, long count) {
    }

    public record SlaComplaintRow(
            Long id,
            String complaintReference,
            String title,
            ComplaintPriority priority,
            String assignedStaff,
            LocalDateTime deadlineAt,
            long secondsUntilDeadline,
            SlaStatus status,
            int escalationLevel,
            List<EscalationHistory> escalationHistory) {
    }

    public record EscalationHistory(int level, LocalDateTime escalatedAt, String reason) {
    }
}
