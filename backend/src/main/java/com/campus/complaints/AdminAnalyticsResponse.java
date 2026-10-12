package com.campus.complaints;

import java.util.List;

public class AdminAnalyticsResponse {

    public record Summary(
            long total,
            long submitted,
            long underReview,
            long assigned,
            long inProgress,
            long resolved,
            long closed,
            long rejected,
            long overdue,
            Double averageResolutionHours,
            long resolvedWithinSlaCount,
            long resolvedAfterSlaCount,
            double resolvedWithinSlaPercentage,
            double resolvedAfterSlaPercentage
    ) {}

    public record CountItem(String label, long count) {}

    public record TrendItem(String period, long count) {}

    public record StaffWorkloadItem(
            Long staffId,
            String staffName,
            String department,
            long assignedCount,
            long inProgressCount,
            long resolvedCount,
            long totalHandled
    ) {}

    public record FilterOption(Long id, String name) {}

    public record FilterOptions(
            List<FilterOption> departments,
            List<FilterOption> categories,
            List<FilterOption> staffMembers
    ) {}

    private final Summary summary;
    private final List<CountItem> byStatus;
    private final List<CountItem> byPriority;
    private final List<CountItem> byCategory;
    private final List<CountItem> byDepartment;
    private final List<CountItem> byEscalationLevel;
    private final List<TrendItem> monthlyTrends;
    private final List<TrendItem> weeklyTrends;
    private final List<StaffWorkloadItem> staffWorkload;
    private final FilterOptions filterOptions;

    public AdminAnalyticsResponse(
            Summary summary,
            List<CountItem> byStatus,
            List<CountItem> byPriority,
            List<CountItem> byCategory,
            List<CountItem> byDepartment,
            List<CountItem> byEscalationLevel,
            List<TrendItem> monthlyTrends,
            List<TrendItem> weeklyTrends,
            List<StaffWorkloadItem> staffWorkload,
            FilterOptions filterOptions) {
        this.summary = summary;
        this.byStatus = byStatus;
        this.byPriority = byPriority;
        this.byCategory = byCategory;
        this.byDepartment = byDepartment;
        this.byEscalationLevel = byEscalationLevel;
        this.monthlyTrends = monthlyTrends;
        this.weeklyTrends = weeklyTrends;
        this.staffWorkload = staffWorkload;
        this.filterOptions = filterOptions;
    }

    public Summary getSummary() { return summary; }
    public List<CountItem> getByStatus() { return byStatus; }
    public List<CountItem> getByPriority() { return byPriority; }
    public List<CountItem> getByCategory() { return byCategory; }
    public List<CountItem> getByDepartment() { return byDepartment; }
    public List<CountItem> getByEscalationLevel() { return byEscalationLevel; }
    public List<TrendItem> getMonthlyTrends() { return monthlyTrends; }
    public List<TrendItem> getWeeklyTrends() { return weeklyTrends; }
    public List<StaffWorkloadItem> getStaffWorkload() { return staffWorkload; }
    public FilterOptions getFilterOptions() { return filterOptions; }
}
