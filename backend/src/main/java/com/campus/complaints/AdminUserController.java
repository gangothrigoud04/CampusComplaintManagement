package com.campus.complaints;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AdminUserController(
            UserRepository userRepository,
            AuthService authService,
            AdminUserService adminUserService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<UserProfileResponse> listUsers() {
        return userRepository.findAll().stream().map(UserProfileResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse provisionUser(
            @Valid @RequestBody AuthRequests.ProvisionUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.provision(request, principal);
    }

    @PutMapping("/{userId}")
    public UserProfileResponse updateUser(
            @PathVariable long userId,
            @Valid @RequestBody AuthRequests.UpdateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return adminUserService.update(userId, request, principal);
    }

    @PatchMapping("/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(
            @PathVariable long userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        adminUserService.deactivate(userId, principal);
    }

    @PatchMapping("/{userId}/activate")
    public UserProfileResponse activateUser(
            @PathVariable long userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return adminUserService.activate(userId, principal);
    }
}
