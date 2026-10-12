package com.campus.complaints;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ComplaintRepositoryTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintCategoryRepository categoryRepository;

    @Autowired
    private CampusLocationRepository locationRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintAssignmentRepository assignmentRepository;

    @Autowired
    private ComplaintStatusHistoryRepository statusHistoryRepository;

    @Test
    void repositoriesPersistAndRetrieveLinkedComplaintRecords() {
        Department department = departmentRepository.save(new Department("Facilities", "Campus services"));
        User reporter = userRepository.save(new User("Sample Student", "student@example.test", UserRole.STUDENT, department));
        User assignee = userRepository.save(new User("Sample Staff", "staff@example.test", UserRole.STAFF, department));
        ComplaintCategory category = categoryRepository.save(new ComplaintCategory("Electricity", "Electrical service"));
        CampusLocation location = locationRepository.save(new CampusLocation("Main Building", "101", "First floor"));
        Complaint complaint = complaintRepository.save(
                new Complaint(reporter, category, location, department, "Light not working", "A light needs attention.",
                        "CMP-TEST-000000000001", ComplaintPriority.MEDIUM, null));
        assignmentRepository.save(new ComplaintAssignment(complaint, assignee, reporter, "Initial assignment"));
        statusHistoryRepository.save(new ComplaintStatusHistory(
                complaint, reporter, null, ComplaintStatus.SUBMITTED, "Complaint recorded"));

        Optional<User> storedReporter = userRepository.findByEmail("student@example.test");
        List<ComplaintAssignment> assignments = assignmentRepository
                .findByComplaint_IdOrderByAssignedAtAsc(complaint.getId());
        List<ComplaintStatusHistory> history = statusHistoryRepository
                .findByComplaint_IdOrderByChangedAtAsc(complaint.getId());

        assertThat(storedReporter).isPresent();
        assertThat(storedReporter.orElseThrow().getDepartment().getName()).isEqualTo("Facilities");
        assertThat(complaintRepository.findById(complaint.getId())).isPresent();
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getAssignee().getRole()).isEqualTo(UserRole.STAFF);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getNewStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
    }
}
