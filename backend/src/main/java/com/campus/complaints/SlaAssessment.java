package com.campus.complaints;

import java.time.Duration;
import java.time.LocalDateTime;

public record SlaAssessment(SlaStatus status, long secondsUntilDeadline) {

    public static SlaAssessment assess(Complaint complaint, LocalDateTime now) {
        LocalDateTime deadline = complaint.getResolutionDeadlineAt();
        if (complaint.getResolvedAt() != null) {
            SlaStatus status = !complaint.getResolvedAt().isAfter(deadline)
                    ? SlaStatus.RESOLVED_WITHIN_SLA
                    : SlaStatus.RESOLVED_AFTER_SLA;
            return new SlaAssessment(status, Duration.between(now, deadline).getSeconds());
        }
        if (!now.isBefore(deadline)) {
            return new SlaAssessment(SlaStatus.OVERDUE, Duration.between(now, deadline).getSeconds());
        }
        boolean dueSoon = !now.isBefore(complaint.getSlaDueSoonAt());
        return new SlaAssessment(
                dueSoon ? SlaStatus.DUE_SOON : SlaStatus.ON_TRACK,
                Duration.between(now, deadline).getSeconds());
    }
}
