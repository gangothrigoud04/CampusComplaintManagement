package com.campus.complaints;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InitialDepartmentRunnerTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    void initializesConfiguredDepartmentOnlyOnceWhenTableIsEmpty() throws Exception {
        InitialDepartmentRunner runner = new InitialDepartmentRunner(departmentRepository, "Official Department");

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.findAll())
                .singleElement()
                .extracting(Department::getName)
                .isEqualTo("Official Department");
    }

    @Test
    void doesNotCreateDepartmentWhenNoNameIsConfigured() throws Exception {
        new InitialDepartmentRunner(departmentRepository, "  ").run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isZero();
    }

    @Test
    void preservesExistingDepartmentsWhenASeedNameIsConfigured() throws Exception {
        Department existing = departmentRepository.save(new Department("Existing Official Department", null));

        new InitialDepartmentRunner(departmentRepository, "Another Department")
                .run(new DefaultApplicationArguments());

        assertThat(departmentRepository.findAll())
                .extracting(Department::getName)
                .containsExactly(existing.getName());
    }
}
