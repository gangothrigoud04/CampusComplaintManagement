package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRoleAndActiveTrueAndDepartment_IdOrderByFullNameAsc(UserRole role, Long departmentId);

    List<User> findByRoleAndActiveTrue(UserRole role);

    boolean existsByEmail(String email);

    boolean existsByRollNumber(String rollNumber);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByRollNumberAndIdNot(String rollNumber, Long id);

    boolean existsByRole(UserRole role);
}
