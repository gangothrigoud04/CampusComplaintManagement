package com.campus.complaints;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/admin/sla")
public class AdminSlaController {

    private final SlaDashboardService slaDashboardService;

    public AdminSlaController(SlaDashboardService slaDashboardService) {
        this.slaDashboardService = slaDashboardService;
    }

    @GetMapping
    public SlaDashboardResponse dashboard(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) SlaFilter status,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) Integer escalationLevel,
            @RequestParam(required = false) Long assignedStaffId,
            @RequestParam(required = false) @Min(0) Integer minimumOverdueHours,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return slaDashboardService.dashboard(principal, status, priority, escalationLevel, assignedStaffId,
                minimumOverdueHours, deadlineFrom, deadlineTo, page, size);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) SlaFilter status,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) Integer escalationLevel,
            @RequestParam(required = false) Long assignedStaffId,
            @RequestParam(required = false) @Min(0) Integer minimumOverdueHours,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo) {
        byte[] csv = slaDashboardService.exportCsv(principal, status, priority, escalationLevel, assignedStaffId,
                minimumOverdueHours, deadlineFrom, deadlineTo);
        String filename = "sla-records-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
