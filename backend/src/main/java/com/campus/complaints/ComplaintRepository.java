package com.campus.complaints;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    @Query("""
            select complaint from Complaint complaint
            where complaint.reporter.role = :reporterRole
              and (:search is null
                   or lower(complaint.complaintCode) like lower(concat('%', :search, '%'))
                   or lower(complaint.title) like lower(concat('%', :search, '%'))
                   or lower(complaint.category.name) like lower(concat('%', :search, '%'))
                   or lower(complaint.location.building) like lower(concat('%', :search, '%'))
                   or lower(coalesce(complaint.location.room, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(complaint.department.name, '')) like lower(concat('%', :search, '%')))
              and (:status is null or complaint.status = :status)
              and (:priority is null or complaint.priority = :priority)
              and (:categoryId is null or complaint.category.id = :categoryId)
              and (:departmentId is null or complaint.department.id = :departmentId)
              and (:startAt is null or complaint.createdAt >= :startAt)
              and (:endBefore is null or complaint.createdAt < :endBefore)
            """)
    Page<Complaint> findCampusStudentComplaints(
            @Param("reporterRole") UserRole reporterRole,
            @Param("search") String search,
            @Param("status") ComplaintStatus status,
            @Param("priority") ComplaintPriority priority,
            @Param("categoryId") Long categoryId,
            @Param("departmentId") Long departmentId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endBefore") LocalDateTime endBefore,
            Pageable pageable);

    @Query("select complaint.status, count(complaint) from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole group by complaint.status")
    List<Object[]> countCampusStudentComplaintsByStatus(@Param("reporterRole") UserRole reporterRole);

    @Query("select complaint.priority, count(complaint) from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole group by complaint.priority")
    List<Object[]> countCampusStudentComplaintsByPriority(@Param("reporterRole") UserRole reporterRole);

    @Query("select complaint.category.name, count(complaint) from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole group by complaint.category.name order by complaint.category.name")
    List<Object[]> countCampusStudentComplaintsByCategory(@Param("reporterRole") UserRole reporterRole);

    @Query("select complaint.department.name, count(complaint) from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole and complaint.department is not null "
            + "group by complaint.department.name order by complaint.department.name")
    List<Object[]> countCampusStudentComplaintsByDepartment(@Param("reporterRole") UserRole reporterRole);

    @Query("select function('year', complaint.createdAt), function('month', complaint.createdAt), count(complaint) "
            + "from Complaint complaint where complaint.reporter.role = :reporterRole "
            + "and complaint.createdAt >= :from group by function('year', complaint.createdAt), "
            + "function('month', complaint.createdAt)")
    List<Object[]> countCampusStudentComplaintsCreatedByMonth(
            @Param("reporterRole") UserRole reporterRole, @Param("from") LocalDateTime from);

    @Query(value = """
            select avg(timestampdiff(HOUR, complaint.created_at, resolved.resolved_at))
            from complaints complaint
            join users reporter on reporter.id = complaint.reporter_id
            join (
                select complaint_id, min(changed_at) as resolved_at
                from complaint_status_history
                where new_status = 'RESOLVED'
                group by complaint_id
            ) resolved on resolved.complaint_id = complaint.id
            where reporter.user_role = 'STUDENT'
            """, nativeQuery = true)
    Double averageCampusStudentResolutionHours();

    @Query("select distinct complaint.category.id, complaint.category.name from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole order by complaint.category.name")
    List<Object[]> findCampusStudentComplaintCategories(@Param("reporterRole") UserRole reporterRole);

    @Query("select distinct complaint.department.id, complaint.department.name from Complaint complaint "
            + "where complaint.reporter.role = :reporterRole and complaint.department is not null "
            + "order by complaint.department.name")
    List<Object[]> findCampusStudentComplaintDepartments(@Param("reporterRole") UserRole reporterRole);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select complaint from Complaint complaint where complaint.id = :complaintId")
    java.util.Optional<Complaint> findByIdForUpdate(@Param("complaintId") Long complaintId);

    @Query("select complaint.id from Complaint complaint "
            + "where complaint.resolvedAt is null "
            + "and complaint.status not in :terminalStatuses "
            + "and complaint.resolutionDeadlineAt <= :now")
    List<Long> findOverdueUnresolvedIds(
            @Param("now") LocalDateTime now,
            @Param("terminalStatuses") Collection<ComplaintStatus> terminalStatuses);

    @Query("select complaint.id from Complaint complaint "
            + "where complaint.resolvedAt is null "
            + "and complaint.status not in :terminalStatuses "
            + "and complaint.slaDueSoonAt <= :now "
            + "and complaint.resolutionDeadlineAt > :now")
    List<Long> findDueSoonUnresolvedIds(
            @Param("now") LocalDateTime now,
            @Param("terminalStatuses") Collection<ComplaintStatus> terminalStatuses);

    long countByEscalationLevel(int escalationLevel);
}
