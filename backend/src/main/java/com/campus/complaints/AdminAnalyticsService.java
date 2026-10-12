package com.campus.complaints;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminAnalyticsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter WEEK_FORMAT = DateTimeFormatter.ofPattern("MMM dd");

    private final ComplaintRepository complaintRepository;
    private final ComplaintAssignmentRepository assignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public AdminAnalyticsService(
            ComplaintRepository complaintRepository,
            ComplaintAssignmentRepository assignmentRepository,
            DepartmentRepository departmentRepository,
            ComplaintCategoryRepository categoryRepository,
            UserRepository userRepository) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    public AdminAnalyticsResponse getAnalytics(
            AuthenticatedUser principal,
            LocalDate from,
            LocalDate to,
            Long departmentId,
            Long categoryId,
            ComplaintPriority priority,
            ComplaintStatus status,
            Long assignedStaffId) {
        requireAdmin(principal);

        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<Long> staffFilteredComplaintIds = null;
        if (assignedStaffId != null) {
            staffFilteredComplaintIds = assignmentRepository
                    .findByAssignee_IdAndUnassignedAtIsNullOrderByAssignedAtDesc(assignedStaffId)
                    .stream()
                    .map(a -> a.getComplaint().getId())
                    .toList();
            if (staffFilteredComplaintIds.isEmpty()) {
                return emptyResponse();
            }
        }

        final List<Long> filterIds = staffFilteredComplaintIds;

        Specification<Complaint> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (filterIds != null) {
                predicates.add(root.get("id").in(filterIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Complaint> complaints = complaintRepository.findAll(spec);

        long total = complaints.size();
        long submitted = 0;
        long underReview = 0;
        long assigned = 0;
        long inProgress = 0;
        long resolved = 0;
        long closed = 0;
        long rejected = 0;
        long overdue = 0;
        long resolvedWithinSla = 0;
        long resolvedAfterSla = 0;
        double totalResolutionHours = 0;
        int resolvedCountWithTime = 0;

        Map<ComplaintStatus, Long> statusCountMap = new EnumMap<>(ComplaintStatus.class);
        Map<ComplaintPriority, Long> priorityCountMap = new EnumMap<>(ComplaintPriority.class);
        Map<String, Long> categoryCountMap = new TreeMap<>();
        Map<String, Long> departmentCountMap = new TreeMap<>();
        Map<Integer, Long> escalationCountMap = new TreeMap<>();

        Map<YearMonth, Long> monthlyTrendMap = new TreeMap<>();
        Map<LocalDate, Long> weeklyTrendMap = new TreeMap<>();

        for (Complaint c : complaints) {
            ComplaintStatus st = c.getStatus();
            statusCountMap.put(st, statusCountMap.getOrDefault(st, 0L) + 1);

            switch (st) {
                case SUBMITTED -> submitted++;
                case UNDER_REVIEW -> underReview++;
                case ASSIGNED -> assigned++;
                case IN_PROGRESS -> inProgress++;
                case RESOLVED -> resolved++;
                case CLOSED -> closed++;
                case REJECTED -> rejected++;
            }

            priorityCountMap.put(c.getPriority(), priorityCountMap.getOrDefault(c.getPriority(), 0L) + 1);

            String catName = c.getCategory() != null ? c.getCategory().getName() : "Uncategorized";
            categoryCountMap.put(catName, categoryCountMap.getOrDefault(catName, 0L) + 1);

            String deptName = c.getDepartment() != null ? c.getDepartment().getName() : "General Campus";
            departmentCountMap.put(deptName, departmentCountMap.getOrDefault(deptName, 0L) + 1);

            int escLevel = c.getEscalationLevel();
            escalationCountMap.put(escLevel, escalationCountMap.getOrDefault(escLevel, 0L) + 1);

            boolean isTerminal = (st == ComplaintStatus.RESOLVED || st == ComplaintStatus.CLOSED);
            if (isTerminal && c.getResolvedAt() != null) {
                Duration duration = Duration.between(c.getCreatedAt(), c.getResolvedAt());
                totalResolutionHours += Math.max(0, duration.toMinutes() / 60.0);
                resolvedCountWithTime++;

                if (!c.getResolvedAt().isAfter(c.getResolutionDeadlineAt())) {
                    resolvedWithinSla++;
                } else {
                    resolvedAfterSla++;
                }
            } else if (!isTerminal && c.getResolutionDeadlineAt() != null && c.getResolutionDeadlineAt().isBefore(now)) {
                overdue++;
            }

            if (c.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(c.getCreatedAt());
                monthlyTrendMap.put(ym, monthlyTrendMap.getOrDefault(ym, 0L) + 1);

                LocalDate weekStart = c.getCreatedAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                weeklyTrendMap.put(weekStart, weeklyTrendMap.getOrDefault(weekStart, 0L) + 1);
            }
        }

        Double avgResolutionHours = resolvedCountWithTime > 0
                ? Math.round((totalResolutionHours / resolvedCountWithTime) * 10.0) / 10.0
                : null;

        long totalResolvedTerminal = resolvedWithinSla + resolvedAfterSla;
        double withinSlaPct = totalResolvedTerminal > 0
                ? Math.round((resolvedWithinSla * 100.0 / totalResolvedTerminal) * 10.0) / 10.0
                : 0.0;
        double afterSlaPct = totalResolvedTerminal > 0
                ? Math.round((resolvedAfterSla * 100.0 / totalResolvedTerminal) * 10.0) / 10.0
                : 0.0;

        AdminAnalyticsResponse.Summary summary = new AdminAnalyticsResponse.Summary(
                total,
                submitted,
                underReview,
                assigned,
                inProgress,
                resolved,
                closed,
                rejected,
                overdue,
                avgResolutionHours,
                resolvedWithinSla,
                resolvedAfterSla,
                withinSlaPct,
                afterSlaPct
        );

        List<AdminAnalyticsResponse.CountItem> byStatus = new ArrayList<>();
        for (ComplaintStatus s : ComplaintStatus.values()) {
            byStatus.add(new AdminAnalyticsResponse.CountItem(s.name(), statusCountMap.getOrDefault(s, 0L)));
        }

        List<AdminAnalyticsResponse.CountItem> byPriority = new ArrayList<>();
        for (ComplaintPriority p : ComplaintPriority.values()) {
            byPriority.add(new AdminAnalyticsResponse.CountItem(p.name(), priorityCountMap.getOrDefault(p, 0L)));
        }

        List<AdminAnalyticsResponse.CountItem> byCategory = categoryCountMap.entrySet().stream()
                .map(e -> new AdminAnalyticsResponse.CountItem(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();

        List<AdminAnalyticsResponse.CountItem> byDepartment = departmentCountMap.entrySet().stream()
                .map(e -> new AdminAnalyticsResponse.CountItem(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();

        List<AdminAnalyticsResponse.CountItem> byEscalationLevel = new ArrayList<>();
        for (int i = 0; i <= 3; i++) {
            byEscalationLevel.add(new AdminAnalyticsResponse.CountItem("Level " + i, escalationCountMap.getOrDefault(i, 0L)));
        }

        List<AdminAnalyticsResponse.TrendItem> monthlyTrends = monthlyTrendMap.entrySet().stream()
                .map(e -> new AdminAnalyticsResponse.TrendItem(e.getKey().format(MONTH_FORMAT), e.getValue()))
                .toList();

        List<AdminAnalyticsResponse.TrendItem> weeklyTrends = weeklyTrendMap.entrySet().stream()
                .map(e -> new AdminAnalyticsResponse.TrendItem("W/C " + e.getKey().format(WEEK_FORMAT), e.getValue()))
                .toList();

        List<User> activeStaff = userRepository.findByRoleAndActiveTrue(UserRole.STAFF);
        List<ComplaintAssignment> allActiveAssignments = assignmentRepository.findByUnassignedAtIsNull();
        Map<Long, List<ComplaintAssignment>> assignmentsByStaff = allActiveAssignments.stream()
                .collect(Collectors.groupingBy(a -> a.getAssignee().getId()));

        List<AdminAnalyticsResponse.StaffWorkloadItem> staffWorkload = new ArrayList<>();
        for (User staffMember : activeStaff) {
            List<ComplaintAssignment> staffAssignments = assignmentsByStaff.getOrDefault(staffMember.getId(), List.of());
            long assignedWork = 0;
            long inProgressWork = 0;
            for (ComplaintAssignment ca : staffAssignments) {
                Complaint ac = ca.getComplaint();
                if (ac.getStatus() == ComplaintStatus.ASSIGNED) assignedWork++;
                else if (ac.getStatus() == ComplaintStatus.IN_PROGRESS) inProgressWork++;
            }
            long totalEverHandled = assignmentRepository.findByAssignee_Id(staffMember.getId()).stream()
                    .map(a -> a.getComplaint().getId())
                    .distinct()
                    .count();
            long resolvedByStaff = assignmentRepository.findByAssignee_Id(staffMember.getId()).stream()
                    .map(ComplaintAssignment::getComplaint)
                    .filter(c -> c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED)
                    .map(Complaint::getId)
                    .distinct()
                    .count();

            String staffDeptName = staffMember.getDepartment() != null ? staffMember.getDepartment().getName() : "—";
            staffWorkload.add(new AdminAnalyticsResponse.StaffWorkloadItem(
                    staffMember.getId(),
                    staffMember.getFullName(),
                    staffDeptName,
                    assignedWork,
                    inProgressWork,
                    resolvedByStaff,
                    totalEverHandled
            ));
        }

        AdminAnalyticsResponse.FilterOptions filterOptions = getFilterOptions(activeStaff);

        return new AdminAnalyticsResponse(
                summary,
                byStatus,
                byPriority,
                byCategory,
                byDepartment,
                byEscalationLevel,
                monthlyTrends,
                weeklyTrends,
                staffWorkload,
                filterOptions
        );
    }

    private AdminAnalyticsResponse.FilterOptions getFilterOptions(List<User> activeStaff) {
        List<AdminAnalyticsResponse.FilterOption> deptOptions = departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(d -> new AdminAnalyticsResponse.FilterOption(d.getId(), d.getName()))
                .toList();
        List<AdminAnalyticsResponse.FilterOption> catOptions = categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new AdminAnalyticsResponse.FilterOption(c.getId(), c.getName()))
                .toList();
        List<AdminAnalyticsResponse.FilterOption> staffOptions = activeStaff.stream()
                .map(s -> new AdminAnalyticsResponse.FilterOption(s.getId(), s.getFullName()))
                .toList();
        return new AdminAnalyticsResponse.FilterOptions(deptOptions, catOptions, staffOptions);
    }

    private AdminAnalyticsResponse emptyResponse() {
        AdminAnalyticsResponse.Summary summary = new AdminAnalyticsResponse.Summary(
                0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0.0, 0.0);
        List<User> activeStaff = userRepository.findByRoleAndActiveTrue(UserRole.STAFF);
        return new AdminAnalyticsResponse(
                summary,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                getFilterOptions(activeStaff)
        );
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required.");
        }
    }
}
