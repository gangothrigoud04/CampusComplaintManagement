package com.campus.complaints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ComplaintRequests {

    private ComplaintRequests() {
    }

    public record SubmitComplaintRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 160) String location,
            @NotNull ComplaintPriority priority) {
    }

    public record AssignmentRequest(
            @NotNull Long departmentId,
            @NotNull Long staffId) {
    }

    public record StatusUpdateRequest(
            @NotNull ComplaintStatus status,
            @Size(max = 1000) String note) {
    }

    public record WorkNoteRequest(@NotBlank @Size(max = 1000) String note) {
    }

    public record ComplaintNoteRequest(
            @NotBlank @Size(max = 1000) String content,
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "Request ID must be a UUID.")
            String requestId) {
    }

    public record ResolutionRequest(@NotBlank @Size(max = 1000) String note) {
    }
}
