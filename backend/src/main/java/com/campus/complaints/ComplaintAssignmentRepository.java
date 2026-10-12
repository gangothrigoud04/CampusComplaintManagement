package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintAssignmentRepository extends JpaRepository<ComplaintAssignment, Long> {
    List<ComplaintAssignment> findByComplaint_IdOrderByAssignedAtAsc(Long complaintId);

    List<ComplaintAssignment> findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(Long complaintId);

    List<ComplaintAssignment> findByComplaint_IdInAndUnassignedAtIsNull(List<Long> complaintIds);

    boolean existsByComplaint_IdAndAssignee_IdAndUnassignedAtIsNull(Long complaintId, Long assigneeId);

    List<ComplaintAssignment> findByAssignee_IdAndUnassignedAtIsNullOrderByAssignedAtDesc(Long assigneeId);

    List<ComplaintAssignment> findByUnassignedAtIsNull();

    List<ComplaintAssignment> findByAssignee_Id(Long assigneeId);
}
