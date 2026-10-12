package com.campus.complaints;

public record AuthenticatedUser(Long id, String email, UserRole role) {
}
