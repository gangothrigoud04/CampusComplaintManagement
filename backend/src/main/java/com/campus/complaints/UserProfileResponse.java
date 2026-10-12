package com.campus.complaints;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long departmentId,
        String rollNumber,
        String className,
        String section,
        boolean active) {

    public static UserProfileResponse from(User user) {
        Long departmentId = user.getDepartment() == null ? null : user.getDepartment().getId();
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                departmentId,
                user.getRollNumber(),
                user.getClassName(),
                user.getSection(),
                user.isActive());
    }
}
