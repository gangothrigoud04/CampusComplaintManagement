package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintWorkNoteRepository extends JpaRepository<ComplaintWorkNote, Long> {
    List<ComplaintWorkNote> findByComplaint_IdOrderByCreatedAtAsc(Long complaintId);

    List<ComplaintWorkNote> findByComplaint_IdAndRecordTypeOrderByCreatedAtAscIdAsc(
            Long complaintId, ComplaintNoteType recordType);

    Optional<ComplaintWorkNote> findByComplaint_IdAndAuthor_IdAndRequestId(
            Long complaintId, Long authorId, String requestId);
}
