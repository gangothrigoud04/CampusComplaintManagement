package com.campus.complaints;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestPart("complaint") ComplaintRequests.SubmitComplaintRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return complaintService.submit(principal, request, image);
    }

    @GetMapping("/categories")
    public List<ComplaintResponse.CategoryOption> categories() {
        return complaintService.categories();
    }

    @GetMapping("/my")
    public List<ComplaintResponse> myComplaints(@AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.myComplaints(principal);
    }

    @GetMapping("/assigned")
    public List<ComplaintResponse> assignedComplaints(@AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.assignedComplaints(principal);
    }

    @GetMapping
    public List<ComplaintResponse> allComplaints(@AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.allComplaints(principal);
    }

    @GetMapping("/{id}")
    public ComplaintResponse getComplaint(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return complaintService.getComplaint(principal, id);
    }

    @PatchMapping("/{id}/status")
    public ComplaintResponse updateStaffStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ComplaintRequests.StatusUpdateRequest request) {
        return complaintService.updateStaffStatus(principal, id, request);
    }

    @PostMapping("/{id}/work-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse addStaffWorkNote(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ComplaintRequests.WorkNoteRequest request) {
        return complaintService.addStaffWorkNote(principal, id, request);
    }

    @GetMapping("/{id}/comments")
    public List<ComplaintNoteResponse> getComments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return complaintService.getComplaintNotes(principal, id, ComplaintNoteType.COMMENT);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintNoteResponse addComment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ComplaintRequests.ComplaintNoteRequest request) {
        return complaintService.addComplaintNote(principal, id, request, ComplaintNoteType.COMMENT);
    }

    @GetMapping("/{id}/resolution-notes")
    public List<ComplaintNoteResponse> getResolutionNotes(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return complaintService.getComplaintNotes(principal, id, ComplaintNoteType.RESOLUTION_NOTE);
    }

    @PostMapping("/{id}/resolution-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintNoteResponse addResolutionNote(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ComplaintRequests.ComplaintNoteRequest request) {
        return complaintService.addComplaintNote(principal, id, request, ComplaintNoteType.RESOLUTION_NOTE);
    }

    @PostMapping(value = "/{id}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintResponse resolveStaffComplaint(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestPart("resolution") ComplaintRequests.ResolutionRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return complaintService.resolveStaffComplaint(principal, id, request, image);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<ByteArrayResource> getImage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        StoredComplaintImage image = complaintService.getImage(principal, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new ByteArrayResource(image.bytes()));
    }

    @GetMapping("/{id}/resolution-proof")
    public ResponseEntity<ByteArrayResource> getResolutionProof(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        StoredComplaintImage image = complaintService.getResolutionProof(principal, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new ByteArrayResource(image.bytes()));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String search) {
        byte[] csv = complaintService.exportComplaintsCsv(principal, status, category, priority, department, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"complaints-export.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
