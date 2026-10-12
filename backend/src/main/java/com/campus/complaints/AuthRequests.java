package com.campus.complaints;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must contain at least one letter and one number")
            String password,
            @NotBlank @Size(max = 40) String rollNumber,
            @Size(max = 100) String className,
            @Size(max = 40) String section,
            @NotNull Long departmentId,
            UserRole role) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record ProvisionUserRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must contain at least one letter and one number")
            String password,
            UserRole role,
            Long departmentId) {
    }

    public record DepartmentOption(Long id, String name) {
    }

    public record CreateDepartmentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateDepartmentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            Long departmentId,
            @Size(max = 40) String rollNumber,
            @Size(max = 100) String className,
            @Size(max = 40) String section) {
    }
}
