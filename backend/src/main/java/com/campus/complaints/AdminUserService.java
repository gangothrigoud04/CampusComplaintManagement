package com.campus.complaints;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public AdminUserService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public UserProfileResponse update(
            long userId, AuthRequests.UpdateUserRequest request, AuthenticatedUser actor) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be edited here.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .filter(Department::isActive)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Choose an active department."));
        }

        String rollNumber = null;
        String className = null;
        String section = null;
        if (user.getRole() == UserRole.STUDENT) {
            if (department == null || isBlank(request.rollNumber())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Student name, email, department and roll number are required.");
            }
            rollNumber = request.rollNumber().trim();
            className = isBlank(request.className()) ? null : request.className().trim();
            section = isBlank(request.section()) ? null : request.section().trim();
            if (userRepository.existsByRollNumberAndIdNot(rollNumber, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This roll number is already in use.");
            }
        }

        Map<String, ?> before = userState(user);
        user.updateProfile(request.name().trim(), email, department, rollNumber, className, section);
        auditService.record(actor, "ACCOUNT_UPDATED", "USER", user.getId().toString(), user.getEmail(),
                null, null, "Updated account " + user.getEmail() + ".",
                before, userState(user));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void deactivate(long userId, AuthenticatedUser actor) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMIN || userId == actor.id()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account cannot be deactivated here.");
        }
        boolean wasActive = user.isActive();
        user.deactivate();
        if (wasActive) {
            auditService.record(actor, "ACCOUNT_DEACTIVATED", "USER", user.getId().toString(), user.getEmail(),
                    null, null, "Deactivated account " + user.getEmail() + ".",
                    Map.of("active", true), Map.of("active", false));
        }
    }

    @Transactional
    public UserProfileResponse activate(long userId, AuthenticatedUser actor) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be activated here.");
        }
        boolean wasActive = user.isActive();
        user.activate();
        if (!wasActive) {
            auditService.record(actor, "ACCOUNT_ACTIVATED", "USER", user.getId().toString(), user.getEmail(),
                    null, null, "Reactivated account " + user.getEmail() + ".",
                    Map.of("active", false), Map.of("active", true));
        }
        return UserProfileResponse.from(user);
    }

    private Map<String, ?> userState(User user) {
        return Map.of(
                "name", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "departmentId", user.getDepartment() == null ? "" : user.getDepartment().getId(),
                "active", user.isActive());
    }

    private User findUser(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
