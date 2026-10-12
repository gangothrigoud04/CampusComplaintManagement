package com.campus.complaints;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialDepartmentRunner implements ApplicationRunner {

    private final DepartmentRepository departmentRepository;
    private final String departmentName;

    public InitialDepartmentRunner(
            DepartmentRepository departmentRepository,
            @Value("${app.bootstrap-department.name:}") String departmentName) {
        this.departmentRepository = departmentRepository;
        this.departmentName = departmentName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String configuredName = departmentName.trim();
        if (configuredName.length() > 120) {
            throw new IllegalStateException("INITIAL_DEPARTMENT_NAME must be 120 characters or fewer.");
        }
        if (!configuredName.isEmpty() && departmentRepository.count() == 0) {
            departmentRepository.save(new Department(configuredName, null));
        }
    }
}
