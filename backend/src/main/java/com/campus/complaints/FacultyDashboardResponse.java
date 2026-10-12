package com.campus.complaints;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FacultyDashboardResponse {
    private FacultyDashboardResponse() {
    }

    public record Summary(
            long total,
            long pending,
            long assigned,
            long inProgress,
            long resolved,
            long rejected,
            double resolutionRate) {
    }

    public record ComplaintRow(
            Long id,
            String complaintId,
            String title,
            String description,
            String category,
            String location,
            String department,
            String reporterName,
            ComplaintPriority priority,
            ComplaintStatus status,
            LocalDateTime submittedAt,
            List<String> assignedStaff) {
    }

    public record ComplaintPage(
            List<ComplaintRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record Count(String label, long count) {
    }

    public record MonthCount(String month, long count) {
    }

    public record Analytics(
            List<Count> byStatus,
            List<Count> byPriority,
            List<Count> byCategory,
            List<Count> byDepartment,
            List<MonthCount> monthlySubmissions,
            Double averageResolutionHours) {
    }

    public record FilterOption(Long id, String name) {
    }

    public record FilterOptions(List<FilterOption> categories, List<FilterOption> departments) {
    }

    public record ComplaintFilters(
            String search,
            ComplaintStatus status,
            ComplaintPriority priority,
            Long categoryId,
            Long departmentId,
            LocalDate from,
            LocalDate to) {
    }
}
