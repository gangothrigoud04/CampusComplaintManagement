package com.campus.complaints;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPolicyTest {

    private final SlaPolicy policy = new SlaPolicy(4, 24, 72, 120, 20);

    @Test
    void usesConfiguredTargetsForEachPriority() {
        assertThat(policy.targetHours(ComplaintPriority.URGENT)).isEqualTo(4);
        assertThat(policy.targetHours(ComplaintPriority.HIGH)).isEqualTo(24);
        assertThat(policy.targetHours(ComplaintPriority.MEDIUM)).isEqualTo(72);
        assertThat(policy.targetHours(ComplaintPriority.LOW)).isEqualTo(120);
    }

    @Test
    void calculatesDueSoonWindowFromTargetDuration() {
        assertThat(policy.dueSoonWindow(4).toMinutes()).isEqualTo(48);
        assertThat(policy.dueSoonWindow(120).toMinutes()).isEqualTo(1440);
    }

    @Test
    void assessesDueSoonAndOverdueBoundaries() {
        Complaint complaint = complaint(ComplaintPriority.HIGH);

        assertThat(SlaAssessment.assess(complaint, complaint.getSlaDueSoonAt().minusSeconds(1)).status())
                .isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaAssessment.assess(complaint, complaint.getSlaDueSoonAt()).status())
                .isEqualTo(SlaStatus.DUE_SOON);
        assertThat(SlaAssessment.assess(complaint, complaint.getResolutionDeadlineAt()).status())
                .isEqualTo(SlaStatus.OVERDUE);
    }

    @Test
    void resolvedComplaintsKeepTheirFinalSlaClassification() {
        Complaint complaint = complaint(ComplaintPriority.URGENT);
        complaint.recordResolution(complaint.getResolutionDeadlineAt());
        assertThat(SlaAssessment.assess(complaint, complaint.getResolutionDeadlineAt().plusDays(10)).status())
                .isEqualTo(SlaStatus.RESOLVED_WITHIN_SLA);

        Complaint lateComplaint = complaint(ComplaintPriority.LOW);
        lateComplaint.recordResolution(lateComplaint.getResolutionDeadlineAt().plusSeconds(1));
        assertThat(SlaAssessment.assess(lateComplaint, lateComplaint.getResolutionDeadlineAt().plusDays(10)).status())
                .isEqualTo(SlaStatus.RESOLVED_AFTER_SLA);
    }

    @Test
    void closedStatusStopsSlaClock() {
        Complaint complaint = complaint(ComplaintPriority.MEDIUM);
        complaint.updateStatus(ComplaintStatus.CLOSED);

        assertThat(complaint.getResolvedAt()).isNotNull();
        assertThat(SlaAssessment.assess(complaint, complaint.getResolutionDeadlineAt().plusDays(1)).status())
                .isEqualTo(SlaStatus.RESOLVED_WITHIN_SLA);
    }

    @Test
    void escalatesImmediatelyAndAtElapsedHourThresholds() {
        assertThat(policy.escalationLevel(-1)).isZero();
        assertThat(policy.escalationLevel(0)).isEqualTo(1);
        assertThat(policy.escalationLevel(23)).isEqualTo(1);
        assertThat(policy.escalationLevel(24)).isEqualTo(2);
        assertThat(policy.escalationLevel(47)).isEqualTo(2);
        assertThat(policy.escalationLevel(48)).isEqualTo(3);
    }

    private Complaint complaint(ComplaintPriority priority) {
        User reporter = new User("SLA Reporter", "sla@example.test", UserRole.STUDENT, null);
        return new Complaint(
                reporter,
                new ComplaintCategory("SLA Test", "Test"),
                new CampusLocation("Test Hall", null, null),
                null,
                "SLA test complaint",
                "Description",
                "CMP-SLA-TEST",
                priority,
                null,
                policy.targetHours(priority));
    }
}
