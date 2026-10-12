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
import java.util.List;

import static com.campus.complaints.FacultyDashboardResponse.*;

@RestController
@RequestMapping("/api/faculty")
public class FacultyDashboardController {
    private final FacultyDashboardService dashboardService;

    public FacultyDashboardController(FacultyDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        return dashboardService.summary(principal);
    }

    @GetMapping("/complaints")
    public ComplaintPage complaints(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedAt") String sort) {
        return dashboardService.complaints(principal,
                new ComplaintFilters(search, status, priority, categoryId, departmentId, from, to), page, size, sort);
    }

    @GetMapping("/filters")
    public FilterOptions filters(@AuthenticationPrincipal AuthenticatedUser principal) {
        return dashboardService.filterOptions(principal);
    }

    @GetMapping("/analytics")
    public Analytics analytics(@AuthenticationPrincipal AuthenticatedUser principal) {
        return dashboardService.analytics(principal);
    }

    @GetMapping(value = "/reports.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ComplaintRow> rows = dashboardService.exportRows(principal,
                new ComplaintFilters(search, status, priority, categoryId, departmentId, from, to));
        StringBuilder csv = new StringBuilder(
                "Complaint ID,Title,Description,Student,Category,Department,Location,Priority,Status,Assigned Staff,Submitted At\r\n");
        for (ComplaintRow row : rows) {
            csv.append(csvCell(row.complaintId())).append(',')
                    .append(csvCell(row.title())).append(',')
                    .append(csvCell(row.description())).append(',')
                    .append(csvCell(row.reporterName())).append(',')
                    .append(csvCell(row.category())).append(',')
                    .append(csvCell(row.department())).append(',')
                    .append(csvCell(row.location())).append(',')
                    .append(csvCell(row.priority().name())).append(',')
                    .append(csvCell(row.status().name())).append(',')
                    .append(csvCell(String.join("; ", row.assignedStaff()))).append(',')
                    .append(csvCell(row.submittedAt().toString())).append("\r\n");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=faculty-complaints.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        int firstContent = 0;
        while (firstContent < safe.length()
                && (Character.isWhitespace(safe.charAt(firstContent)) || Character.isISOControl(safe.charAt(firstContent)))) {
            firstContent++;
        }
        if (firstContent < safe.length() && "=+-@".indexOf(safe.charAt(firstContent)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
