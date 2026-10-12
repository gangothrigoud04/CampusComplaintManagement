package com.campus.complaints;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminAssignmentController {

    private final ComplaintService complaintService;

    public AdminAssignmentController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping("/assignment-options/staff")
    public List<ComplaintService.EligibleStaff> eligibleStaff(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long departmentId) {
        return complaintService.eligibleStaff(principal, departmentId);
    }

    @PutMapping("/complaints/{complaintId}/assignment")
    public ComplaintResponse assign(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long complaintId,
            @Valid @RequestBody ComplaintRequests.AssignmentRequest request) {
        return complaintService.assignComplaint(principal, complaintId, request);
    }
}
