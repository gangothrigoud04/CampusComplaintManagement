package com.campus.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_sla_escalations", uniqueConstraints = @UniqueConstraint(
        name = "uk_complaint_sla_escalation_level", columnNames = {"complaint_id", "escalation_level"}))
public class ComplaintSlaEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "escalated_at", nullable = false)
    private LocalDateTime escalatedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    protected ComplaintSlaEscalation() {
    }

    public ComplaintSlaEscalation(
            Complaint complaint, int escalationLevel, LocalDateTime escalatedAt, String reason) {
        this.complaint = complaint;
        this.escalationLevel = escalationLevel;
        this.escalatedAt = escalatedAt;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public String getReason() {
        return reason;
    }
}
