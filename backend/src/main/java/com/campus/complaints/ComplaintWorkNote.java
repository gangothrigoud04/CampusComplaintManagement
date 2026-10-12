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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_work_notes", uniqueConstraints = @UniqueConstraint(
        name = "uk_complaint_work_notes_request",
        columnNames = {"complaint_id", "author_id", "request_id"}))
public class ComplaintWorkNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private ComplaintNoteType recordType;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ComplaintWorkNote() {
    }

    public ComplaintWorkNote(Complaint complaint, User author, String note) {
        this(complaint, author, note, ComplaintNoteType.WORK_NOTE);
    }

    public ComplaintWorkNote(
            Complaint complaint, User author, String note, ComplaintNoteType recordType) {
        this(complaint, author, note, recordType, null);
    }

    public ComplaintWorkNote(
            Complaint complaint, User author, String note, ComplaintNoteType recordType, String requestId) {
        this.complaint = complaint;
        this.author = author;
        this.note = note;
        this.recordType = recordType;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ComplaintNoteType getRecordType() {
        return recordType;
    }

    public String getRequestId() {
        return requestId;
    }
}
