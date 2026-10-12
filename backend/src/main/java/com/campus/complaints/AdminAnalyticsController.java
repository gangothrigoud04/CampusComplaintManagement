package com.campus.complaints;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public AdminAnalyticsResponse getAnalytics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) Long assignedStaffId) {
        return analyticsService.getAnalytics(
                principal, from, to, departmentId, categoryId, priority, status, assignedStaffId);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) Long assignedStaffId) {
        AdminAnalyticsResponse response = analyticsService.getAnalytics(
                principal, from, to, departmentId, categoryId, priority, status, assignedStaffId);

        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value\r\n");
        AdminAnalyticsResponse.Summary s = response.getSummary();
        csv.append("Total Complaints,").append(s.total()).append("\r\n");
        csv.append("Submitted,").append(s.submitted()).append("\r\n");
        csv.append("Under Review,").append(s.underReview()).append("\r\n");
        csv.append("Assigned,").append(s.assigned()).append("\r\n");
        csv.append("In Progress,").append(s.inProgress()).append("\r\n");
        csv.append("Resolved,").append(s.resolved()).append("\r\n");
        csv.append("Closed,").append(s.closed()).append("\r\n");
        csv.append("Rejected,").append(s.rejected()).append("\r\n");
        csv.append("Overdue,").append(s.overdue()).append("\r\n");
        csv.append("Avg Resolution Hours,").append(s.averageResolutionHours() != null ? s.averageResolutionHours() : "N/A").append("\r\n");
        csv.append("Resolved Within SLA,").append(s.resolvedWithinSlaCount()).append(" (").append(s.resolvedWithinSlaPercentage()).append("%)\r\n");
        csv.append("Resolved After SLA,").append(s.resolvedAfterSlaCount()).append(" (").append(s.resolvedAfterSlaPercentage()).append("%)\r\n");
        csv.append("\r\n");

        csv.append("Category,Count\r\n");
        for (AdminAnalyticsResponse.CountItem item : response.getByCategory()) {
            csv.append(escapeCsv(item.label())).append(',').append(item.count()).append("\r\n");
        }
        csv.append("\r\n");

        csv.append("Department,Count\r\n");
        for (AdminAnalyticsResponse.CountItem item : response.getByDepartment()) {
            csv.append(escapeCsv(item.label())).append(',').append(item.count()).append("\r\n");
        }
        csv.append("\r\n");

        csv.append("Staff Name,Department,Assigned,In Progress,Resolved,Total Handled\r\n");
        for (AdminAnalyticsResponse.StaffWorkloadItem w : response.getStaffWorkload()) {
            csv.append(escapeCsv(w.staffName())).append(',')
                    .append(escapeCsv(w.department())).append(',')
                    .append(w.assignedCount()).append(',')
                    .append(w.inProgressCount()).append(',')
                    .append(w.resolvedCount()).append(',')
                    .append(w.totalHandled()).append("\r\n");
        }

        String filename = "admin-analytics-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-") || escaped.startsWith("@")) {
            escaped = "'" + escaped;
        }
        return "\"" + escaped + "\"";
    }
}
