package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintSlaEscalationRepository extends JpaRepository<ComplaintSlaEscalation, Long> {
    boolean existsByComplaint_IdAndEscalationLevel(Long complaintId, int escalationLevel);

    List<ComplaintSlaEscalation> findByComplaint_IdOrderByEscalatedAtAscIdAsc(Long complaintId);

    List<ComplaintSlaEscalation> findByComplaint_IdInOrderByEscalatedAtAscIdAsc(List<Long> complaintIds);
}
