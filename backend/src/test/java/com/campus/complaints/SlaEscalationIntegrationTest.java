package com.campus.complaints;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SlaEscalationIntegrationTest {

    @Autowired
    private SlaEscalationService escalationService;

    @Autowired
    private SlaDashboardService dashboardService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintSlaEscalationRepository escalationRepository;

    @Autowired
    private ComplaintAssignmentRepository assignmentRepository;

    @Autowired
    private ComplaintCategoryRepository categoryRepository;

    @Autowired
    private CampusLocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void overdueComplaintsEscalateOnceAndNotifyOnlyActiveAdministrators() {
        User admin = userRepository.saveAndFlush(new User(
                "SLA Admin", "sla-admin@example.test", UserRole.ADMIN, null));
        User inactiveAdmin = userRepository.saveAndFlush(new User(
                "Inactive SLA Admin", "inactive-sla-admin@example.test", UserRole.ADMIN, null));
        inactiveAdmin.deactivate();
        userRepository.saveAndFlush(inactiveAdmin);
        User student = userRepository.saveAndFlush(new User(
                "SLA Student", "sla-student@example.test", UserRole.STUDENT, null));
        User staff = userRepository.saveAndFlush(new User(
                "SLA Staff", "sla-staff@example.test", UserRole.STAFF, null));
        Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                student,
                categoryRepository.saveAndFlush(new ComplaintCategory("SLA Escalation", "Integration test")),
                locationRepository.saveAndFlush(new CampusLocation("SLA Hall", null, null)),
                null,
                "Overdue complaint",
                "Integration test complaint",
                "CMP-SLA-ESCALATION",
                ComplaintPriority.HIGH,
                null));
        assignmentRepository.saveAndFlush(new ComplaintAssignment(complaint, staff, admin, null));
        LocalDateTime overdueByMoreThan48Hours = LocalDateTime.now(ZoneOffset.UTC).minusHours(72);
        jdbcTemplate.update(
                "update complaints set resolution_deadline_at = ? where id = ?",
                overdueByMoreThan48Hours,
                complaint.getId());
        entityManager.clear();

        escalationService.processOverdueComplaints();
        escalationService.processOverdueComplaints();

        List<ComplaintSlaEscalation> history =
                escalationRepository.findByComplaint_IdOrderByEscalatedAtAscIdAsc(complaint.getId());
        assertThat(history).extracting(ComplaintSlaEscalation::getEscalationLevel).containsExactly(1, 2, 3);
        assertThat(complaintRepository.findById(complaint.getId()).orElseThrow().getEscalationLevel()).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.getId())).isEqualTo(3);
        assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(inactiveAdmin.getId())).isZero();
        assertThat(auditEntryRepository.findAll().stream()
                .filter(entry -> complaint.getId().equals(entry.getComplaintId()))
                .filter(entry -> "SLA_ESCALATED".equals(entry.getActionType())))
                .hasSize(3)
                .allSatisfy(entry -> {
                    assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
                    assertThat(entry.getActorUserId()).isNull();
                });

        SlaDashboardResponse dashboard = dashboardService.dashboard(
                new AuthenticatedUser(admin.getId(), admin.getEmail(), UserRole.ADMIN),
                SlaFilter.OVERDUE,
                ComplaintPriority.HIGH,
                3,
                null,
                48,
                overdueByMoreThan48Hours.toLocalDate(),
                overdueByMoreThan48Hours.toLocalDate(),
                0,
                20);
        assertThat(dashboard.overdue()).isEqualTo(1);
        assertThat(dashboard.complaints().getTotalElements()).isEqualTo(1);
        assertThat(dashboard.complaints().getContent().get(0).escalationHistory()).hasSize(3);
        SlaDashboardResponse assignedDashboard = dashboardService.dashboard(
                new AuthenticatedUser(admin.getId(), admin.getEmail(), UserRole.ADMIN),
                null,
                null,
                null,
                staff.getId(),
                null,
                null,
                null,
                0,
                20);
        assertThat(assignedDashboard.complaints().getTotalElements()).isEqualTo(1);
        assertThat(assignedDashboard.complaints().getContent().get(0).assignedStaff()).isEqualTo("SLA Staff");
    }

    @Test
    void slaDashboardRequiresAdministratorAccess() throws Exception {
        User student = userRepository.saveAndFlush(new User(
                "SLA Access Student", "sla-access-student@example.test", UserRole.STUDENT, null));
        User admin = userRepository.saveAndFlush(new User(
                "SLA Access Admin", "sla-access-admin@example.test", UserRole.ADMIN, null));

        mockMvc.perform(get("/api/admin/sla"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/sla")
                        .header("Authorization", "Bearer " + jwtService.createAccessToken(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/sla?page=0&size=20")
                        .header("Authorization", "Bearer " + jwtService.createAccessToken(admin)))
                .andExpect(status().isOk());
    }
}
