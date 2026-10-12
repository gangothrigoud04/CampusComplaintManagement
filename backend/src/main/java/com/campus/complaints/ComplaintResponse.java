package com.campus.complaints;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintResponse(
        Long id,
        String complaintId,
        String title,
        String description,
        Long categoryId,
        String category,
        String location,
        ComplaintPriority priority,
        ComplaintStatus status,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        boolean hasImage,
        String imageUrl,
        String department,
        List<String> assignedStaff,
        List<StatusHistoryEntry> statusHistory,
        String reporterName,
        String reporterRollNumber,
        CurrentAssignment currentAssignment,
        List<WorkNoteEntry> workNotes,
        ResolutionProofEntry resolutionProof,
        SlaSummary sla) {

    public static ComplaintResponse from(Complaint complaint) {
        return from(complaint, List.of(), List.of(), null, null);
    }

    public static ComplaintResponse from(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory) {
        return from(complaint, assignedStaff, statusHistory, null, null, null, List.of());
    }

    public static ComplaintResponse from(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            List<WorkNoteEntry> workNotes) {
        return from(complaint, assignedStaff, statusHistory, null, null, null, workNotes);
    }

    public static ComplaintResponse fromDetails(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            List<WorkNoteEntry> workNotes,
            ResolutionProofEntry resolutionProof) {
        return from(complaint, assignedStaff, statusHistory, null, null, null, workNotes, resolutionProof);
    }

    public static ComplaintResponse fromFacultyDetails(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            List<WorkNoteEntry> workNotes,
            ResolutionProofEntry resolutionProof,
            CurrentAssignment currentAssignment) {
        return from(complaint, assignedStaff, statusHistory, complaint.getReporter().getFullName(), null,
                currentAssignment, workNotes, resolutionProof);
    }

    public static ComplaintResponse fromAdmin(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            CurrentAssignment currentAssignment) {
        User reporter = complaint.getReporter();
        return from(complaint, assignedStaff, statusHistory,
                reporter.getFullName(), reporter.getRollNumber(), currentAssignment, List.of());
    }

    public static ComplaintResponse fromAdmin(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            CurrentAssignment currentAssignment,
            List<WorkNoteEntry> workNotes) {
        User reporter = complaint.getReporter();
        return from(complaint, assignedStaff, statusHistory,
                reporter.getFullName(), reporter.getRollNumber(), currentAssignment, workNotes, null);
    }

    public static ComplaintResponse fromAdmin(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            CurrentAssignment currentAssignment,
            List<WorkNoteEntry> workNotes,
            ResolutionProofEntry resolutionProof) {
        User reporter = complaint.getReporter();
        return from(complaint, assignedStaff, statusHistory,
                reporter.getFullName(), reporter.getRollNumber(), currentAssignment, workNotes, resolutionProof);
    }

    private static ComplaintResponse from(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            String reporterName,
            String reporterRollNumber) {
        return from(complaint, assignedStaff, statusHistory, reporterName, reporterRollNumber, null, List.of());
    }

    private static ComplaintResponse from(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            String reporterName,
            String reporterRollNumber,
            CurrentAssignment currentAssignment,
            List<WorkNoteEntry> workNotes) {
        return from(complaint, assignedStaff, statusHistory, reporterName, reporterRollNumber,
                currentAssignment, workNotes, null);
    }

    private static ComplaintResponse from(
            Complaint complaint,
            List<String> assignedStaff,
            List<StatusHistoryEntry> statusHistory,
            String reporterName,
            String reporterRollNumber,
            CurrentAssignment currentAssignment,
            List<WorkNoteEntry> workNotes,
            ResolutionProofEntry resolutionProof) {
        SlaAssessment slaAssessment = SlaAssessment.assess(
                complaint, LocalDateTime.now(java.time.Clock.systemUTC()));
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getComplaintCode(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCategory().getId(),
                complaint.getCategory().getName(),
                locationLabel(complaint.getLocation()),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                complaint.getImageReference() != null,
                complaint.getImageReference() == null ? null
                        : "/api/complaints/" + complaint.getId() + "/image",
                complaint.getDepartment() == null ? null : complaint.getDepartment().getName(),
                List.copyOf(assignedStaff),
                List.copyOf(statusHistory),
                reporterName,
                reporterRollNumber,
                currentAssignment,
                List.copyOf(workNotes),
                resolutionProof,
                new SlaSummary(
                        complaint.getSlaTargetHours(),
                        complaint.getResolutionDeadlineAt(),
                        complaint.getResolvedAt(),
                        slaAssessment.status(),
                        slaAssessment.secondsUntilDeadline(),
                        complaint.getEscalationLevel(),
                        complaint.getLastEscalationAt()));
    }

    public record CategoryOption(Long id, String name) {
    }

    public record StatusHistoryEntry(
            ComplaintStatus previousStatus,
            ComplaintStatus status,
            LocalDateTime changedAt,
            String actorName,
            String note) {
    }

    public record CurrentAssignment(Long departmentId, Long staffId, String staffName, LocalDateTime assignedAt) {
    }

    public record WorkNoteEntry(String authorName, String note, LocalDateTime createdAt) {
    }

    public record ResolutionProofEntry(
            String imageUrl,
            String staffName,
            LocalDateTime uploadedAt,
            String note) {
    }

    public record SlaSummary(
            int targetHours,
            LocalDateTime deadlineAt,
            LocalDateTime resolvedAt,
            SlaStatus status,
            long secondsUntilDeadline,
            int escalationLevel,
            LocalDateTime lastEscalationAt) {
    }

    private static String locationLabel(CampusLocation location) {
        return location.getRoom() == null || location.getRoom().isBlank()
                ? location.getBuilding()
                : location.getBuilding() + ", " + location.getRoom();
    }
}
