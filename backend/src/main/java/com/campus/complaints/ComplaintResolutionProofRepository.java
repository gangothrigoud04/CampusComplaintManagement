package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplaintResolutionProofRepository extends JpaRepository<ComplaintResolutionProof, Long> {
    Optional<ComplaintResolutionProof> findByComplaint_Id(Long complaintId);
}
