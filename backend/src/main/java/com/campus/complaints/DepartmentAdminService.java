package com.campus.complaints;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class DepartmentAdminService {

    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public DepartmentAdminService(DepartmentRepository departmentRepository, AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentRecord> list() {
        return departmentRepository.findAll().stream()
                .map(DepartmentRecord::from)
                .toList();
    }

    @Transactional
    public DepartmentRecord create(AuthRequests.CreateDepartmentRequest request, AuthenticatedUser actor) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A department with this name already exists.");
        }
        Department department = departmentRepository.saveAndFlush(
                new Department(name, request.description() == null ? null : request.description().trim()));
        auditService.record(actor, "DEPARTMENT_CREATED", "DEPARTMENT", department.getId().toString(),
                department.getName(), null, null, "Created department " + department.getName() + ".",
                null, departmentState(department));
        return DepartmentRecord.from(department);
    }

    @Transactional
    public DepartmentRecord update(
            long departmentId, AuthRequests.UpdateDepartmentRequest request, AuthenticatedUser actor) {
        Department department = findDepartment(departmentId);
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, departmentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A department with this name already exists.");
        }
        Map<String, ?> before = departmentState(department);
        department.update(name, request.description() == null ? null : request.description().trim());
        auditService.record(actor, "DEPARTMENT_UPDATED", "DEPARTMENT", department.getId().toString(),
                department.getName(), null, null, "Updated department " + department.getName() + ".",
                before, departmentState(department));
        return DepartmentRecord.from(department);
    }

    @Transactional
    public void deactivate(long departmentId, AuthenticatedUser actor) {
        Department department = findDepartment(departmentId);
        boolean wasActive = department.isActive();
        department.deactivate();
        if (wasActive) {
            auditService.record(actor, "DEPARTMENT_DEACTIVATED", "DEPARTMENT", department.getId().toString(),
                    department.getName(), null, null, "Deactivated department " + department.getName() + ".",
                    Map.of("active", true), Map.of("active", false));
        }
    }

    @Transactional
    public DepartmentRecord activate(long departmentId, AuthenticatedUser actor) {
        Department department = findDepartment(departmentId);
        boolean wasActive = department.isActive();
        department.activate();
        if (!wasActive) {
            auditService.record(actor, "DEPARTMENT_ACTIVATED", "DEPARTMENT", department.getId().toString(),
                    department.getName(), null, null, "Reactivated department " + department.getName() + ".",
                    Map.of("active", false), Map.of("active", true));
        }
        return DepartmentRecord.from(department);
    }

    private Map<String, ?> departmentState(Department department) {
        return Map.of("name", department.getName(),
                "description", department.getDescription() == null ? "" : department.getDescription(),
                "active", department.isActive());
    }

    private Department findDepartment(long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
    }

    public record DepartmentRecord(Long id, String name, String description, boolean active) {
        static DepartmentRecord from(Department department) {
            return new DepartmentRecord(
                    department.getId(), department.getName(), department.getDescription(), department.isActive());
        }
    }
}
