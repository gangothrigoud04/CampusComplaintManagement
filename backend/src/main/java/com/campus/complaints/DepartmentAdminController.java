package com.campus.complaints;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentAdminController {

    private final DepartmentAdminService departmentAdminService;

    public DepartmentAdminController(DepartmentAdminService departmentAdminService) {
        this.departmentAdminService = departmentAdminService;
    }

    @GetMapping
    public List<DepartmentAdminService.DepartmentRecord> list() {
        return departmentAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentAdminService.DepartmentRecord create(
            @Valid @RequestBody AuthRequests.CreateDepartmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return departmentAdminService.create(request, principal);
    }

    @PutMapping("/{departmentId}")
    public DepartmentAdminService.DepartmentRecord update(
            @PathVariable long departmentId,
            @Valid @RequestBody AuthRequests.UpdateDepartmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return departmentAdminService.update(departmentId, request, principal);
    }

    @PatchMapping("/{departmentId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable long departmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        departmentAdminService.deactivate(departmentId, principal);
    }

    @PatchMapping("/{departmentId}/activate")
    public DepartmentAdminService.DepartmentRecord activate(
            @PathVariable long departmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return departmentAdminService.activate(departmentId, principal);
    }
}
