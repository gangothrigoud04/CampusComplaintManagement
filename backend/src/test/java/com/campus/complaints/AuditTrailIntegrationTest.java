package com.campus.complaints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditTrailIntegrationTest {

    private static final String PASSWORD = "AuditTest98765";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintCategoryRepository categoryRepository;

    @Autowired
    private CampusLocationRepository locationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Test
    void successfulAccountAndDepartmentChangesAreAuditedAndAdminsOnlyCanSearch() throws Exception {
        User administrator = createUser(UserRole.ADMIN);
        String adminToken = login(administrator);
        String departmentName = "Audit department " + UUID.randomUUID();

        MvcResult departmentResponse = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + departmentName + "\",\"description\":\"Audit test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long departmentId = objectMapper.readTree(departmentResponse.getResponse().getContentAsString())
                .get("id").asLong();
        String renamedDepartment = departmentName + " Updated";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/departments/{departmentId}", departmentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + renamedDepartment + "\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/departments/{departmentId}/deactivate", departmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/departments/{departmentId}/activate", departmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String staffEmail = "audit.staff." + UUID.randomUUID() + "@example.test";
        MvcResult staffResponse = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audit Staff\",\"email\":\"" + staffEmail
                                + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"STAFF\",\"departmentId\":" + departmentId
                                + ",\"actorUserId\":999999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        long staffId = objectMapper.readTree(staffResponse.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/users/{userId}", staffId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Audit Staff\",\"email\":\"" + staffEmail
                                + "\",\"departmentId\":" + departmentId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{userId}/deactivate", staffId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{userId}/activate", staffId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "ACCOUNT_CREATED")
                        .param("role", "ADMIN")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorUserId").value(administrator.getId()))
                .andExpect(jsonPath("$.content[0].actorName").value(administrator.getFullName()))
                .andExpect(jsonPath("$.content[0].actorRole").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].actionType").value("ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.content[0].entityReference").value(staffEmail))
                .andExpect(jsonPath("$.content[0].afterState").isNotEmpty())
                .andExpect(jsonPath("$.content[0].afterState").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PASSWORD))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        assertThat(auditEntryRepository.findAll())
                .extracting(AuditEntry::getActionType)
                .contains("DEPARTMENT_CREATED", "DEPARTMENT_UPDATED", "DEPARTMENT_DEACTIVATED",
                        "DEPARTMENT_ACTIVATED", "ACCOUNT_CREATED", "ACCOUNT_UPDATED",
                        "ACCOUNT_DEACTIVATED", "ACCOUNT_ACTIVATED");

        for (UserRole role : new UserRole[]{UserRole.STAFF, UserRole.FACULTY, UserRole.STUDENT}) {
            String token = login(createUser(role));
            mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void successfulComplaintCommentCreatesSafeAuditRecord() throws Exception {
        User student = createUser(UserRole.STUDENT);
        ComplaintCategory category = new ComplaintCategory(
                "Audit comment " + UUID.randomUUID(), "Audit integration test");
        category = categoryRepository.saveAndFlush(category);
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Audit comment " + UUID.randomUUID(), null, null));
        Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, null, "Audit comment complaint", "Complaint description",
                "CMP-" + UUID.randomUUID(), ComplaintPriority.LOW, null));
        String comment = "This private comment text must not be copied into the audit.";

        mockMvc.perform(post("/api/complaints/{id}/comments", complaint.getId())
                        .header("Authorization", "Bearer " + login(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + comment + "\",\"requestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        AuditEntry audit = auditEntryRepository.findAll().stream()
                .filter(entry -> entry.getActionType().equals("COMMENT_ADDED"))
                .findFirst()
                .orElseThrow();
        assertThat(audit.getActorUserId()).isEqualTo(student.getId());
        assertThat(audit.getActorRole()).isEqualTo("STUDENT");
        assertThat(audit.getComplaintId()).isEqualTo(complaint.getId());
        assertThat(audit.getComplaintReference()).isEqualTo(complaint.getComplaintCode());
        assertThat(audit.getEntityReference()).isEqualTo(complaint.getComplaintCode());
        assertThat(audit.getDescription()).isEqualTo("Complaint comment added.")
                .doesNotContain(comment);
        assertThat(audit.getBeforeState()).isNull();
        assertThat(audit.getAfterState()).contains("COMMENT").doesNotContain(comment);

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + login(createUser(UserRole.ADMIN)))
                        .param("action", "COMMENT_ADDED")
                        .param("role", "STUDENT")
                        .param("search", complaint.getComplaintCode().toLowerCase())
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].complaintReference").value(complaint.getComplaintCode()));
    }

    @Test
    void studentAndFacultyComplaintSubmissionsAreAuditedAndFilterable() throws Exception {
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Audit submission " + UUID.randomUUID(), "Audit test"));
        String studentTitle = "Student audit submission " + UUID.randomUUID();
        String facultyTitle = "Faculty audit submission " + UUID.randomUUID();
        String studentCode = submitComplaint(createUser(UserRole.STUDENT), category.getId(), studentTitle);
        String facultyCode = submitComplaint(createUser(UserRole.FACULTY), category.getId(), facultyTitle);

        assertThat(auditEntryRepository.findAll().stream()
                .filter(entry -> "COMPLAINT_SUBMITTED".equals(entry.getActionType()))
                .map(AuditEntry::getComplaintReference))
                .containsExactlyInAnyOrder(studentCode, facultyCode);

        String adminToken = login(createUser(UserRole.ADMIN));
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "COMPLAINT_SUBMITTED")
                        .param("role", "FACULTY")
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].complaintReference").value(facultyCode))
                .andExpect(jsonPath("$.content[0].actorRole").value("FACULTY"));
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", studentCode.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].complaintReference").value(studentCode));
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "COMMENT_ADDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void complaintLifecycleActionsCreateAuditRecordsAndExportCsv() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Audit Dept " + UUID.randomUUID(), "Test department"));
        User staff = createUser(UserRole.STAFF, department);
        User student = createUser(UserRole.STUDENT, department);
        User admin = createUser(UserRole.ADMIN);

        String adminToken = login(admin);
        String staffToken = login(staff);
        String studentToken = login(student);

        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Lifecycle Category " + UUID.randomUUID(), "Audit test"));

        // 1. Submit complaint
        MvcResult submitResult = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Lifecycle Complaint " + UUID.randomUUID()))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode submitBody = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        long complaintId = submitBody.get("id").asLong();
        String complaintCode = submitBody.get("complaintId").asText();

        // 2. Admin assigns complaint
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + department.getId() + ",\"staffId\":" + staff.getId() + "}"))
                .andExpect(status().isOk());

        // 3a. Staff accepts assignment (moves to ASSIGNED)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\",\"note\":\"Acknowledging assignment\"}"))
                .andExpect(status().isOk());

        // 3b. Staff moves status to IN_PROGRESS
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"Investigating issue\"}"))
                .andExpect(status().isOk());

        // 4. Staff adds work note
        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Replaced the damaged cable\"}"))
                .andExpect(status().isCreated());

        // 5. Staff resolves complaint with resolution proof image
        MockPart resolutionPart = new MockPart("resolution",
                "{\"note\":\"Issue resolved successfully.\"}".getBytes(StandardCharsets.UTF_8));
        resolutionPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart)
                        .file(new MockMultipartFile("image", "proof.png", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // 6. Admin closes resolved complaint
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"note\":\"Admin verified and closed.\"}"))
                .andExpect(status().isOk());

        // 7. Verify all actions in audit repository
        assertThat(auditEntryRepository.findAll().stream()
                .filter(entry -> complaintId == (entry.getComplaintId() != null ? entry.getComplaintId() : -1))
                .map(AuditEntry::getActionType))
                .contains(
                        "COMPLAINT_SUBMITTED",
                        "COMPLAINT_ASSIGNED",
                        "COMPLAINT_STATUS_CHANGED",
                        "WORK_NOTE_ADDED",
                        "RESOLUTION_PROOF_UPLOADED",
                        "COMPLAINT_RESOLVED"
                );

        // 8. Search audit trail by complaint code
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", complaintCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));

        // 9. Export CSV
        MvcResult csvResult = mockMvc.perform(get("/api/admin/audit/export.csv")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", complaintCode))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn();
        String csvContent = csvResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csvContent).contains("Audit ID,Timestamp,Actor Name,Actor Role,Action Type");
        assertThat(csvContent).contains("COMPLAINT_ASSIGNED");
        assertThat(csvContent).contains("COMPLAINT_RESOLVED");

        // 10. Role checks on export.csv
        mockMvc.perform(get("/api/admin/audit/export.csv")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit/export.csv"))
                .andExpect(status().isUnauthorized());
    }

    private String submitComplaint(User reporter, Long categoryId, String title) throws Exception {
        String token = login(reporter);
        MvcResult response = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(categoryId, title))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        return body.get("complaintId").asText();
    }

    private MockPart complaintPart(Long categoryId, String title) {
        MockPart part = new MockPart("complaint", (
                "{\"title\":\"" + title + "\",\"description\":\"Audit integration test complaint\","
                        + "\"categoryId\":" + categoryId
                        + ",\"location\":\"Audit Test Hall\",\"priority\":\"LOW\"}")
                .getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private User createUser(UserRole role) {
        return createUser(role, null);
    }

    private User createUser(UserRole role, Department department) {
        String unique = UUID.randomUUID().toString();
        User user = new User("Audit " + role, role.name().toLowerCase() + "." + unique + "@example.test",
                role, department);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
