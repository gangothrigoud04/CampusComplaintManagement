package com.campus.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_resolution_proofs")
public class ComplaintResolutionProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(name = "image_reference", nullable = false, unique = true, length = 80)
    private String imageReference;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected ComplaintResolutionProof() {
    }

    public ComplaintResolutionProof(Complaint complaint, User uploadedBy, String imageReference) {
        this.complaint = complaint;
        this.uploadedBy = uploadedBy;
        this.imageReference = imageReference;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getImageReference() {
        return imageReference;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
