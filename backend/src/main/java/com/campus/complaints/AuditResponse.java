package com.campus.complaints;

import java.time.LocalDateTime;

public record AuditResponse(
        Long id,
        Long actorUserId,
        String actorName,
        String actorRole,
        String actionType,
        String entityType,
        String entityId,
        String entityReference,
        Long complaintId,
        String complaintReference,
        String description,
        String beforeState,
        String afterState,
        LocalDateTime createdAt) {

    static AuditResponse from(AuditEntry entry) {
        return new AuditResponse(
                entry.getId(),
                entry.getActorUserId(),
                entry.getActorName(),
                entry.getActorRole(),
                entry.getActionType(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getEntityReference(),
                entry.getComplaintId(),
                entry.getComplaintReference(),
                entry.getDescription(),
                entry.getBeforeState(),
                entry.getAfterState(),
                entry.getCreatedAt());
    }
}
