package com.campus.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_trail")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 36)
    private String eventKey;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", nullable = false, length = 160)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(name = "action_type", nullable = false, length = 60)
    private String actionType;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "entity_reference", length = 160)
    private String entityReference;

    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "complaint_reference", length = 40)
    private String complaintReference;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEntry() {
    }

    public AuditEntry(
            String eventKey,
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
            String afterState) {
        this.eventKey = eventKey;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityReference = entityReference;
        this.complaintId = complaintId;
        this.complaintReference = complaintReference;
        this.description = description;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }

    public Long getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getActionType() {
        return actionType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public String getComplaintReference() {
        return complaintReference;
    }

    public String getDescription() {
        return description;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
