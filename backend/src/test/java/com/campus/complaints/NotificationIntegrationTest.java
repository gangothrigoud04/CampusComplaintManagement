package com.campus.complaints;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintCategoryRepository categoryRepository;

    @Autowired
    private CampusLocationRepository locationRepository;

    @Autowired
    private ComplaintAssignmentRepository assignmentRepository;

    @Autowired
    private ComplaintStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private JwtService jwtService;

    private User student;
    private User faculty;
    private User otherFaculty;
    private User assignedStaff;
    private User replacementStaff;
    private User admin;
    private Complaint studentComplaint;
    private Complaint facultyComplaint;

    @BeforeEach
    void setUp() {
        student = saveUser(UserRole.STUDENT);
        faculty = saveUser(UserRole.FACULTY);
        otherFaculty = saveUser(UserRole.FACULTY);
        assignedStaff = saveUser(UserRole.STAFF);
        replacementStaff = saveUser(UserRole.STAFF);
        admin = saveUser(UserRole.ADMIN);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Notification category " + UUID.randomUUID(), "Test"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Notification location " + UUID.randomUUID(), null, null));
        studentComplaint = saveComplaint(student, category, location);
        facultyComplaint = saveComplaint(faculty, category, location);
    }

    @Test
    void complaintSubmissionNotifiesReporterAdministratorsAndStudentComplaintMonitors() throws Exception {
        notificationService.complaintSubmitted(studentComplaint);
        notificationService.complaintSubmitted(studentComplaint);

        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(student.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(faculty.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(otherFaculty.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(assignedStaff.getId())).isZero();

        String studentToken = jwtService.createAccessToken(student);
        String adminToken = jwtService.createAccessToken(admin);
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].complaintId").value(studentComplaint.getId()))
                .andExpect(jsonPath("$.content[0].read").value(false));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        Notification studentNotification = notificationRepository
                .findByRecipient_Id(student.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().getFirst();
        mockMvc.perform(patch("/api/notifications/{id}/read", studentNotification.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignmentAndStatusEventsNotifyComplaintParticipantsAndStudentComplaintMonitors() throws Exception {
        ComplaintAssignment previous = assignmentRepository.saveAndFlush(
                new ComplaintAssignment(studentComplaint, assignedStaff, admin, null));
        previous.unassign();
        ComplaintAssignment current = assignmentRepository.saveAndFlush(
                new ComplaintAssignment(studentComplaint, replacementStaff, admin, null));
        notificationService.assignmentChanged(studentComplaint, current, List.of(previous));
        notificationService.assignmentChanged(studentComplaint, current, List.of(previous));

        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(replacementStaff.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(assignedStaff.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(faculty.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(otherFaculty.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(student.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.getId())).isEqualTo(1);

        ComplaintStatusHistory history = statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                studentComplaint, replacementStaff, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS,
                "Work started."));
        notificationService.statusChanged(studentComplaint, history);
        notificationService.statusChanged(studentComplaint, history);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(replacementStaff.getId())).isEqualTo(2);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(faculty.getId())).isEqualTo(2);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(otherFaculty.getId())).isEqualTo(2);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(student.getId())).isEqualTo(2);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.getId())).isEqualTo(2);

        ComplaintStatusHistory resolved = statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                studentComplaint, replacementStaff, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED,
                "Resolution completed."));
        notificationService.statusChanged(studentComplaint, resolved);
        notificationService.statusChanged(studentComplaint, resolved);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(replacementStaff.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(faculty.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(otherFaculty.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(student.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.getId())).isEqualTo(3);

        String facultyToken = jwtService.createAccessToken(faculty);
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(facultyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[?(@.type == 'COMPLAINT_STATUS_UPDATED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.type == 'COMPLAINT_RESOLVED')]").isNotEmpty());
        assertUnreadCount(student, 3);
        assertUnreadCount(admin, 3);
        assertUnreadCount(replacementStaff, 3);
        assertUnreadCount(assignedStaff, 1);
        assertUnreadCount(faculty, 3);
        assertUnreadCount(otherFaculty, 3);
    }

    @Test
    void facultyNotificationsFollowTheExistingStudentComplaintMonitoringPolicy() {
        ComplaintAssignment assignment = assignmentRepository.saveAndFlush(
                new ComplaintAssignment(facultyComplaint, assignedStaff, admin, null));
        notificationService.complaintSubmitted(facultyComplaint);
        notificationService.assignmentChanged(facultyComplaint, assignment, List.of());
        ComplaintStatusHistory history = statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                facultyComplaint, assignedStaff, ComplaintStatus.SUBMITTED, ComplaintStatus.ASSIGNED,
                "Assigned."));
        notificationService.statusChanged(facultyComplaint, history);

        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(faculty.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(otherFaculty.getId())).isZero();
    }

    @Test
    void unreadCountIsAuthenticatedAndScopedToEverySupportedRole() throws Exception {
        notificationRepository.saveAndFlush(new Notification(
                student, studentComplaint, "Student 1", "Student notification.", "TEST", "student-count-1"));
        notificationRepository.saveAndFlush(new Notification(
                student, studentComplaint, "Student 2", "Student notification.", "TEST", "student-count-2"));
        notificationRepository.saveAndFlush(new Notification(
                faculty, facultyComplaint, "Faculty", "Faculty notification.", "TEST", "faculty-count"));
        notificationRepository.saveAndFlush(new Notification(
                assignedStaff, studentComplaint, "Staff", "Staff notification.", "TEST", "staff-count"));
        notificationRepository.saveAndFlush(new Notification(
                admin, studentComplaint, "Admin", "Admin notification.", "TEST", "admin-count"));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(jwtService.createAccessToken(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(jwtService.createAccessToken(faculty))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(jwtService.createAccessToken(assignedStaff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(jwtService.createAccessToken(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    private User saveUser(UserRole role) {
        return userRepository.saveAndFlush(new User(
                role + " notification test",
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                role,
                null));
    }

    private Complaint saveComplaint(User reporter, ComplaintCategory category, CampusLocation location) {
        return complaintRepository.saveAndFlush(new Complaint(
                reporter,
                category,
                location,
                null,
                "Notification test complaint",
                "Test description",
                "CMP-" + UUID.randomUUID(),
                ComplaintPriority.LOW,
                null));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void assertUnreadCount(User user, int count) throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(jwtService.createAccessToken(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(count));
    }
}
