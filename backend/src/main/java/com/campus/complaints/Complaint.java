package com.campus.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_code", nullable = false, unique = true, length = 40)
    private String complaintCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ComplaintCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private CampusLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ComplaintPriority priority = ComplaintPriority.MEDIUM;

    @Column(name = "image_reference", length = 80)
    private String imageReference;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "sla_target_hours", nullable = false)
    private int slaTargetHours = 72;

    @Column(name = "sla_due_soon_at", nullable = false)
    private LocalDateTime slaDueSoonAt;

    @Column(name = "resolution_deadline_at", nullable = false)
    private LocalDateTime resolutionDeadlineAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "last_escalation_at")
    private LocalDateTime lastEscalationAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Complaint() {
    }

    public Complaint(User reporter, ComplaintCategory category, CampusLocation location,
                     Department department, String title, String description, String complaintCode,
                     ComplaintPriority priority, String imageReference) {
        this(reporter, category, location, department, title, description, complaintCode, priority,
                imageReference, defaultTargetHours(priority));
    }

    public Complaint(User reporter, ComplaintCategory category, CampusLocation location,
                     Department department, String title, String description, String complaintCode,
                     ComplaintPriority priority, String imageReference, int slaTargetHours) {
        this.reporter = reporter;
        this.category = category;
        this.location = location;
        this.department = department;
        this.title = title;
        this.description = description;
        this.complaintCode = complaintCode;
        this.priority = priority;
        this.imageReference = imageReference;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        setSlaTargetHours(slaTargetHours);
    }

    public Long getId() {
        return id;
    }

    public String getComplaintCode() {
        return complaintCode;
    }

    public User getReporter() {
        return reporter;
    }

    public ComplaintCategory getCategory() {
        return category;
    }

    public CampusLocation getLocation() {
        return location;
    }

    public Department getDepartment() {
        return department;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public ComplaintPriority getPriority() {
        return priority;
    }

    public String getImageReference() {
        return imageReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getSlaTargetHours() {
        return slaTargetHours;
    }

    public LocalDateTime getResolutionDeadlineAt() {
        return resolutionDeadlineAt;
    }

    public LocalDateTime getSlaDueSoonAt() {
        return slaDueSoonAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public LocalDateTime getLastEscalationAt() {
        return lastEscalationAt;
    }

    public void setSlaTargetHours(int slaTargetHours) {
        setSlaTargetHours(slaTargetHours, 20);
    }

    public void setSlaTargetHours(int slaTargetHours, int dueSoonPercent) {
        if (slaTargetHours <= 0 || slaTargetHours > 32767) {
            throw new IllegalArgumentException("SLA target hours must be between 1 and 32767.");
        }
        if (dueSoonPercent <= 0 || dueSoonPercent > 100) {
            throw new IllegalArgumentException("SLA due-soon percentage must be between 1 and 100.");
        }
        this.slaTargetHours = slaTargetHours;
        this.resolutionDeadlineAt = createdAt.plusHours(slaTargetHours);
        this.slaDueSoonAt = resolutionDeadlineAt.minusMinutes((long) slaTargetHours * 60 * dueSoonPercent / 100);
    }

    public void recordResolution(LocalDateTime resolvedAt) {
        if (this.resolvedAt == null) {
            this.resolvedAt = resolvedAt;
        }
    }

    public void recordEscalation(int escalationLevel, LocalDateTime escalatedAt) {
        if (escalationLevel < this.escalationLevel || escalationLevel > 3) {
            throw new IllegalArgumentException("SLA escalation level must progress from 0 through 3.");
        }
        this.escalationLevel = escalationLevel;
        this.lastEscalationAt = escalatedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void assignToDepartment(Department department) {
        this.department = department;
    }

    public void updateStatus(ComplaintStatus status) {
        this.status = status;
        if (status == ComplaintStatus.RESOLVED || status == ComplaintStatus.CLOSED) {
            recordResolution(LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    private static int defaultTargetHours(ComplaintPriority priority) {
        return switch (priority) {
            case URGENT -> 4;
            case HIGH -> 24;
            case MEDIUM -> 72;
            case LOW -> 120;
        };
    }
}
