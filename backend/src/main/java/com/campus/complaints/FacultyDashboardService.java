package com.campus.complaints;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.campus.complaints.FacultyDashboardResponse.*;

@Service
@Transactional(readOnly = true)
public class FacultyDashboardService {
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final ComplaintRepository complaintRepository;
    private final ComplaintAssignmentRepository assignmentRepository;

    public FacultyDashboardService(
            ComplaintRepository complaintRepository,
            ComplaintAssignmentRepository assignmentRepository) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public Summary summary(AuthenticatedUser principal) {
        requireFaculty(principal);
        Map<ComplaintStatus, Long> counts = statusCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long resolved = counts.getOrDefault(ComplaintStatus.RESOLVED, 0L)
                + counts.getOrDefault(ComplaintStatus.CLOSED, 0L);
        long pending = counts.getOrDefault(ComplaintStatus.SUBMITTED, 0L)
                + counts.getOrDefault(ComplaintStatus.UNDER_REVIEW, 0L);
        long assigned = counts.getOrDefault(ComplaintStatus.ASSIGNED, 0L);
        long inProgress = counts.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L);
        long rejected = counts.getOrDefault(ComplaintStatus.REJECTED, 0L);
        return new Summary(total, pending, assigned, inProgress, resolved, rejected,
                total == 0 ? 0 : (resolved * 100.0) / total);
    }

    public ComplaintPage complaints(
            AuthenticatedUser principal, ComplaintFilters filters, int page, int size, String sort) {
        requireFaculty(principal);
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100.");
        }
        Sort.Direction direction;
        String property = switch (sort == null ? "submittedAt" : sort) {
            case "submittedAt" -> "createdAt";
            case "priority" -> "priority";
            case "status" -> "status";
            case "updatedAt" -> "updatedAt";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        };
        direction = Sort.Direction.DESC;
        validateDateRange(filters);
        Page<Complaint> results = query(filters, PageRequest.of(page, size, Sort.by(direction, property)));
        Map<Long, List<String>> activeAssignees = activeAssignees(results.getContent());
        List<ComplaintRow> rows = results.getContent().stream().map(complaint -> new ComplaintRow(
                complaint.getId(),
                complaint.getComplaintCode(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCategory().getName(),
                complaint.getLocation().getRoom() == null || complaint.getLocation().getRoom().isBlank()
                        ? complaint.getLocation().getBuilding()
                        : complaint.getLocation().getBuilding() + ", " + complaint.getLocation().getRoom(),
                complaint.getDepartment() == null ? null : complaint.getDepartment().getName(),
                complaint.getReporter().getFullName(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getCreatedAt(),
                activeAssignees.getOrDefault(complaint.getId(), List.of())))
                .toList();
        return new ComplaintPage(rows, results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages());
    }

    public Analytics analytics(AuthenticatedUser principal) {
        requireFaculty(principal);
        Map<ComplaintStatus, Long> statusCounts = statusCounts();
        List<Count> statuses = new ArrayList<>();
        for (ComplaintStatus status : ComplaintStatus.values()) {
            statuses.add(new Count(status.name(), statusCounts.getOrDefault(status, 0L)));
        }
        LocalDate firstMonth = YearMonth.now().minusMonths(11).atDay(1);
        Map<YearMonth, Long> monthlyCounts = new HashMap<>();
        for (Object[] row : complaintRepository.countCampusStudentComplaintsCreatedByMonth(
                UserRole.STUDENT, firstMonth.atStartOfDay())) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            monthlyCounts.put(YearMonth.of(year, month), ((Number) row[2]).longValue());
        }
        List<MonthCount> monthly = new ArrayList<>();
        YearMonth current = YearMonth.from(firstMonth);
        for (int i = 0; i < 12; i++, current = current.plusMonths(1)) {
            monthly.add(new MonthCount(current.format(MONTH_FORMAT), monthlyCounts.getOrDefault(current, 0L)));
        }
        Double averageHours = complaintRepository.averageCampusStudentResolutionHours();
        return new Analytics(
                statuses,
                counts(complaintRepository.countCampusStudentComplaintsByPriority(UserRole.STUDENT)),
                counts(complaintRepository.countCampusStudentComplaintsByCategory(UserRole.STUDENT)),
                counts(complaintRepository.countCampusStudentComplaintsByDepartment(UserRole.STUDENT)),
                monthly,
                averageHours);
    }

    public FilterOptions filterOptions(AuthenticatedUser principal) {
        requireFaculty(principal);
        return new FilterOptions(
                options(complaintRepository.findCampusStudentComplaintCategories(UserRole.STUDENT)),
                options(complaintRepository.findCampusStudentComplaintDepartments(UserRole.STUDENT)));
    }

    public List<ComplaintRow> exportRows(AuthenticatedUser principal, ComplaintFilters filters) {
        requireFaculty(principal);
        validateDateRange(filters);
        Page<Complaint> results = query(filters,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "createdAt")));
        if (results.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Apply filters to export no more than 5,000 complaint records at a time.");
        }
        Map<Long, List<String>> activeAssignees = activeAssignees(results.getContent());
        return results.getContent().stream().map(complaint -> new ComplaintRow(
                complaint.getId(),
                complaint.getComplaintCode(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCategory().getName(),
                complaint.getLocation().getRoom() == null || complaint.getLocation().getRoom().isBlank()
                        ? complaint.getLocation().getBuilding()
                        : complaint.getLocation().getBuilding() + ", " + complaint.getLocation().getRoom(),
                complaint.getDepartment() == null ? null : complaint.getDepartment().getName(),
                complaint.getReporter().getFullName(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getCreatedAt(),
                activeAssignees.getOrDefault(complaint.getId(), List.of())))
                .toList();
    }

    private Page<Complaint> query(ComplaintFilters filters, PageRequest pageRequest) {
        if ((filters.categoryId() != null && filters.categoryId() < 1)
                || (filters.departmentId() != null && filters.departmentId() < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter IDs must be positive.");
        }
        LocalDateTime from = filters.from() == null ? null : filters.from().atStartOfDay();
        LocalDateTime to = null;
        if (filters.to() != null) {
            try {
                to = filters.to().plusDays(1).atStartOfDay();
            } catch (java.time.DateTimeException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date is outside the supported range.");
            }
        }
        String search = filters.search() == null || filters.search().isBlank()
                ? null : filters.search().trim();
        if (search != null && search.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 120 characters or fewer.");
        }
        return complaintRepository.findCampusStudentComplaints(
                UserRole.STUDENT, search, filters.status(), filters.priority(), filters.categoryId(),
                filters.departmentId(), from, to, pageRequest);
    }

    private Map<Long, List<String>> activeAssignees(List<Complaint> complaints) {
        if (complaints.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = complaints.stream().map(Complaint::getId).toList();
        Map<Long, List<String>> byComplaint = new HashMap<>();
        assignmentRepository.findByComplaint_IdInAndUnassignedAtIsNull(ids).forEach(assignment ->
                byComplaint.computeIfAbsent(assignment.getComplaint().getId(), ignored -> new ArrayList<>())
                        .add(assignment.getAssignee().getFullName()));
        return byComplaint;
    }

    private Map<ComplaintStatus, Long> statusCounts() {
        Map<ComplaintStatus, Long> counts = new EnumMap<>(ComplaintStatus.class);
        complaintRepository.countCampusStudentComplaintsByStatus(UserRole.STUDENT).forEach(row ->
                counts.put((ComplaintStatus) row[0], ((Number) row[1]).longValue()));
        return counts;
    }

    private List<Count> counts(List<Object[]> rows) {
        return rows.stream().map(row -> new Count(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }

    private List<FilterOption> options(List<Object[]> rows) {
        return rows.stream().map(row -> new FilterOption(
                ((Number) row[0]).longValue(), String.valueOf(row[1]))).toList();
    }

    private void validateDateRange(ComplaintFilters filters) {
        if (filters.from() != null && filters.to() != null && filters.from().isAfter(filters.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must not be after end date.");
        }
    }

    private void requireFaculty(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.FACULTY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Faculty access is required.");
        }
    }
}
