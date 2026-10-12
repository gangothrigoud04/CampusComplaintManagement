package com.campus.complaints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockPart;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComplaintFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ComplaintCategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private CampusLocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private ComplaintStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ComplaintWorkNoteRepository workNoteRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ComplaintAssignmentRepository assignmentRepository;

    @Autowired
    private ComplaintResolutionProofRepository resolutionProofRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private ComplaintImageStorage imageStorage;

    @Value("${app.upload.directory}")
    private String uploadDirectory;

    private final List<String> storedTestImages = new ArrayList<>();

    @AfterEach
    void removeStoredTestImages() {
        storedTestImages.forEach(imageStorage::delete);
        storedTestImages.clear();
    }

    @Test
    void studentAndFacultyCanViewStudentComplaintImageButOtherStudentCannot() throws Exception {
        String token = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Test Electricity", "Test category"));
        String imageReference = null;
        try {
            MvcResult result = mockMvc.perform(multipart("/api/complaints")
                            .part(complaintPart(category.getId(), "Broken light", "Light is out", "Main Hall", "HIGH"))
                            .file(new MockMultipartFile("image", "light.png", "image/png", pngImage()))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.complaintId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andExpect(jsonPath("$.hasImage").value(true))
                    .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            long complaintId = response.get("id").asLong();
            String complaintCode = response.get("complaintId").asText();
            Complaint complaint = complaintRepository.findById(complaintId).orElseThrow();
            imageReference = complaint.getImageReference();

            assertThat(response.get("imageUrl").asText())
                    .isEqualTo("/api/complaints/" + complaintId + "/image")
                    .doesNotContain("blob:");
            assertThat(complaint.getComplaintCode()).isEqualTo(complaintCode);
            assertThat(complaint.getReporter().getEmail()).contains("@example.test");
            assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
            assertThat(complaint.getImageReference()).isNotBlank();
            assertThat(Files.exists(Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(imageReference))).isTrue();
            assertThat(Files.size(Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(imageReference)))
                    .isGreaterThan(0);
            assertThat(new ComplaintImageStorage(uploadDirectory).read(imageReference).bytes())
                    .containsExactly(pngImage());
            assertThat(statusHistoryRepository.findByComplaint_IdOrderByChangedAtAsc(complaintId))
                    .extracting(ComplaintStatusHistory::getNewStatus)
                    .containsExactly(ComplaintStatus.SUBMITTED);

            mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].complaintId").value(complaintCode));
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType()).isEqualTo("image/png"));
            mockMvc.perform(get("/api/complaints/{id}", complaintId))
                    .andExpect(status().isUnauthorized());

            String facultyToken = loginToken(roleUser(UserRole.FACULTY, null));
            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + facultyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reporterName").value(complaint.getReporter().getFullName()));
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + facultyToken))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                            .isEqualTo("image/png"));

            Department department = departmentRepository.saveAndFlush(
                    new Department("Attachment access " + UUID.randomUUID(), "Test department"));
            User assignedStaff = roleUser(UserRole.STAFF, department);
            User otherStaff = roleUser(UserRole.STAFF, department);
            User admin = roleUser(UserRole.ADMIN, null);
            assignmentRepository.saveAndFlush(
                    new ComplaintAssignment(complaint, assignedStaff, null, "Image access test"));
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + loginToken(assignedStaff)))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                            .isEqualTo("image/png"));
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + loginToken(admin)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + loginToken(otherStaff)))
                    .andExpect(status().isForbidden());

            String otherStudentToken = registerStudentToken();
            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + otherStudentToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + otherStudentToken))
                    .andExpect(status().isForbidden());
        } finally {
            imageStorage.delete(imageReference);
        }
    }

    @Test
    void missingStoredComplaintImageReturnsNotFoundWithoutAFalseSuccess() throws Exception {
        User student = roleUser(UserRole.STUDENT, null);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Missing attachment " + UUID.randomUUID(), "Test category"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Missing attachment " + UUID.randomUUID(), null, null));
        Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, student.getDepartment(), "Missing image test",
                "The reference intentionally does not exist in test upload storage.",
                "CMP-" + UUID.randomUUID(), ComplaintPriority.LOW,
                UUID.randomUUID() + ".jpg"));

        mockMvc.perform(get("/api/complaints/{id}/image", complaint.getId())
                        .header("Authorization", "Bearer " + loginToken(student)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("The saved complaint image is missing from upload storage."));
    }

    @Test
    void submissionWithoutImagePersistsAndReturnsImageUnavailable() throws Exception {
        String token = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Test Cleaning", "Test category"));
        MvcResult result = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Clean hallway", "Hallway needs cleaning", "Library", "MEDIUM"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.hasImage").value(false))
                .andReturn();
        long complaintId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void complaintHistoryCanMapLazyRelationsOutsideTestTransaction() throws Exception {
        String token = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Test History " + UUID.randomUUID(), "Test category"));
        MvcResult result = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "History test", "Verify history response", "Test Hall", "LOW"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode submitted = objectMapper.readTree(result.getResponse().getContentAsString());

        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].complaintId").value(submitted.get("complaintId").asText()))
                .andExpect(jsonPath("$[0].category").value(category.getName()))
                .andExpect(jsonPath("$[0].location").value("Test Hall"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void facultyCanLoadAuthorizedStudentComplaintImageOutsideTestTransaction() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Faculty image " + UUID.randomUUID(), "Test department"));
        User student = roleUser(UserRole.STUDENT, department);
        User faculty = roleUser(UserRole.FACULTY, department);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Faculty image " + UUID.randomUUID(), "Test category"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Faculty image " + UUID.randomUUID(), null, null));
        String imageReference = imageStorage.store(
                new MockMultipartFile("image", "student.png", "image/png", pngImage()));
        storedTestImages.add(imageReference);
        Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, department, "Student complaint image",
                "Faculty may review authorized student complaints.",
                "CMP-" + UUID.randomUUID(), ComplaintPriority.MEDIUM, imageReference));
        String facultyToken = loginToken(faculty);

        mockMvc.perform(get("/api/complaints/{id}/image", complaint.getId())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                        .isEqualTo("image/png"));
    }

    @Test
    void studentComplaintDetailsIncludeStoredDepartmentAndStatusHistory() throws Exception {
        String token = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Test Details " + UUID.randomUUID(), "Test category"));
        MvcResult submitted = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Detailed report", "Stored description", "Test Hall", "HIGH"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(submitted.getResponse().getContentAsString());
        long complaintId = created.get("id").asLong();

        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(created.get("complaintId").asText()))
                .andExpect(jsonPath("$.title").value("Detailed report"))
                .andExpect(jsonPath("$.description").value("Stored description"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.department").isNotEmpty())
                .andExpect(jsonPath("$.assignedStaff").isArray())
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.statusHistory[0].changedAt").isNotEmpty());
    }

    @Test
    void studentCanLoadAnEmptyOwnListButUnauthenticatedListAccessIsRejected() throws Exception {
        String token = registerStudentToken();

        mockMvc.perform(get("/api/complaints/my"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminCanListComplaintsFromMultipleStudentsAndViewTheirDetails() throws Exception {
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Admin Test " + UUID.randomUUID(), "Admin complaint test"));
        String firstStudentToken = registerStudentToken();
        String secondStudentToken = registerStudentToken();
        long firstComplaintId = submitComplaint(firstStudentToken, category.getId(), "First student's light");
        long secondComplaintId = submitComplaint(secondStudentToken, category.getId(), "Second student's water");
        String studentToken = registerStudentToken();
        String facultyToken = roleToken(UserRole.FACULTY);
        String staffToken = roleToken(UserRole.STAFF);
        String adminToken = roleToken(UserRole.ADMIN);

        mockMvc.perform(get("/api/complaints"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/complaints").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        MvcResult adminList = mockMvc.perform(get("/api/complaints")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(adminList.getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        JsonNode first = findByComplaintId(list, firstComplaintId);
        JsonNode second = findByComplaintId(list, secondComplaintId);
        assertThat(first.get("reporterName").asText()).isEqualTo("Complaint Test Student");
        assertThat(first.get("reporterRollNumber").asText()).isNotBlank();
        assertThat(second.get("reporterRollNumber").asText()).isNotBlank();
        assertThat(first.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(first.get("department").asText()).isNotBlank();
        assertThat(first.get("assignedStaff").isArray()).isTrue();

        mockMvc.perform(get("/api/complaints/{id}", firstComplaintId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporterName").value("Complaint Test Student"))
                .andExpect(jsonPath("$.reporterRollNumber").isNotEmpty())
                .andExpect(jsonPath("$.statusHistory.length()").value(1));
        mockMvc.perform(get("/api/complaints/{id}", firstComplaintId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAssignAndReassignComplaintWithoutChangingItsStatus() throws Exception {
        Department firstDepartment = departmentRepository.saveAndFlush(
                new Department("Assignment A " + UUID.randomUUID(), "Test department"));
        Department secondDepartment = departmentRepository.saveAndFlush(
                new Department("Assignment B " + UUID.randomUUID(), "Test department"));
        User firstStaff = roleUser(UserRole.STAFF, firstDepartment);
        User secondStaff = roleUser(UserRole.STAFF, secondDepartment);
        String firstStudentToken = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Assignment category " + UUID.randomUUID(), "Test category"));
        long complaintId = submitComplaint(firstStudentToken, category.getId(), "Assignment test complaint");
        String adminToken = roleToken(UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/assignment-options/staff")
                        .param("departmentId", firstDepartment.getId().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstStaff.getId()))
                .andExpect(jsonPath("$[0].name").value(firstStaff.getFullName()))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/admin/assignment-options/staff")
                        .param("departmentId", firstDepartment.getId().toString())
                        .header("Authorization", "Bearer " + firstStudentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssignment.staffId").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + firstStaff.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.department").value(firstDepartment.getName()))
                .andExpect(jsonPath("$.currentAssignment.staffId").value(firstStaff.getId()))
                .andExpect(jsonPath("$.currentAssignment.staffName").value(firstStaff.getFullName()));

        assertThat(assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaintId))
                .extracting(assignment -> assignment.getAssignee().getId())
                .containsExactly(firstStaff.getId());
        assertThat(statusHistoryRepository.findByComplaint_IdOrderByChangedAtAsc(complaintId))
                .extracting(ComplaintStatusHistory::getNewStatus)
                .containsExactly(ComplaintStatus.SUBMITTED);

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + secondDepartment.getId()
                                + ",\"staffId\":" + secondStaff.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.department").value(secondDepartment.getName()))
                .andExpect(jsonPath("$.currentAssignment.staffId").value(secondStaff.getId()));
        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssignment.departmentId").value(secondDepartment.getId()))
                .andExpect(jsonPath("$.currentAssignment.staffId").value(secondStaff.getId()));

        var assignmentHistory = assignmentRepository.findByComplaint_IdOrderByAssignedAtAsc(complaintId);
        assertThat(assignmentHistory).hasSize(2);
        assertThat(assignmentHistory.get(0).getUnassignedAt()).isNotNull();
        assertThat(assignmentHistory.get(1).getUnassignedAt()).isNull();
        assertThat(assignmentHistory.get(1).getAssignee().getId()).isEqualTo(secondStaff.getId());
        assertThat(auditEntryRepository.findAll().stream()
                .filter(entry -> entry.getComplaintId() != null && complaintId == entry.getComplaintId())
                .map(AuditEntry::getActionType))
                .containsExactlyInAnyOrder(
                        "COMPLAINT_SUBMITTED", "COMPLAINT_ASSIGNED", "COMPLAINT_ASSIGNED");
    }

    @Test
    void assignmentRejectsInvalidComplaintStaffDepartmentAndNonAdminCallers() throws Exception {
        Department firstDepartment = departmentRepository.saveAndFlush(
                new Department("Validation A " + UUID.randomUUID(), "Test department"));
        Department secondDepartment = departmentRepository.saveAndFlush(
                new Department("Validation B " + UUID.randomUUID(), "Test department"));
        User activeStaff = roleUser(UserRole.STAFF, firstDepartment);
        User wrongDepartmentStaff = roleUser(UserRole.STAFF, secondDepartment);
        User inactiveStaff = roleUser(UserRole.STAFF, firstDepartment);
        User student = roleUser(UserRole.STUDENT, firstDepartment);
        inactiveStaff.deactivate();
        userRepository.saveAndFlush(inactiveStaff);
        secondDepartment.deactivate();
        departmentRepository.saveAndFlush(secondDepartment);
        User faculty = roleUser(UserRole.FACULTY, firstDepartment);
        String studentToken = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Assignment validation " + UUID.randomUUID(), "Test category"));
        long complaintId = submitComplaint(studentToken, category.getId(), "Assignment validation complaint");
        User admin = roleUser(UserRole.ADMIN, null);
        String adminToken = loginToken(admin);
        String staffToken = loginToken(activeStaff);
        String facultyToken = loginToken(faculty);

        mockMvc.perform(get("/api/admin/assignment-options/staff")
                        .param("departmentId", firstDepartment.getId().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activeStaff.getId()));

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + activeStaff.getId() + "}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + activeStaff.getId() + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + activeStaff.getId() + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + activeStaff.getId() + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + activeStaff.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + inactiveStaff.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + faculty.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + student.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + admin.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + firstDepartment.getId()
                                + ",\"staffId\":" + wrongDepartmentStaff.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + secondDepartment.getId()
                                + ",\"staffId\":" + wrongDepartmentStaff.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffAssignedComplaintListUsesOnlyTheAuthenticatedStaffIdentity() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Staff scope " + UUID.randomUUID(), "Test department"));
        User firstStaff = roleUser(UserRole.STAFF, department);
        User secondStaff = roleUser(UserRole.STAFF, department);
        String studentToken = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Staff scope category " + UUID.randomUUID(), "Test category"));
        long complaintId = submitComplaint(studentToken, category.getId(), "Staff private assignment");
        String adminToken = roleToken(UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + department.getId()
                                + ",\"staffId\":" + firstStaff.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/complaints/assigned?userId=" + firstStaff.getId())
                        .header("Authorization", "Bearer " + loginToken(firstStaff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(complaintId))
                .andExpect(jsonPath("$[0].assignedStaff[0]").value(firstStaff.getFullName()));
        mockMvc.perform(get("/api/complaints/assigned?userId=" + firstStaff.getId())
                        .header("Authorization", "Bearer " + loginToken(secondStaff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void assignedStaffCanRecordValidStatusTransitionsAndPersistentWorkNotes() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Staff work " + UUID.randomUUID(), "Test department"));
        User staff = roleUser(UserRole.STAFF, department);
        User otherStaff = roleUser(UserRole.STAFF, department);
        String studentToken = registerStudentToken();
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Staff work category " + UUID.randomUUID(), "Test category"));
        long complaintId = submitComplaintWithImage(studentToken, category.getId(), "Staff workflow");
        String originalImageReference = complaintRepository.findById(complaintId).orElseThrow().getImageReference();
        storedTestImages.add(originalImageReference);
        String staffToken = loginToken(staff);
        String adminToken = roleToken(UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + department.getId()
                                + ",\"staffId\":" + staff.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"  Inspected the switch in Block I.  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.workNotes.length()").value(1))
                .andExpect(jsonPath("$.workNotes[0].authorName").value(staff.getFullName()))
                .andExpect(jsonPath("$.workNotes[0].note").value("Inspected the switch in Block I."));

        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + loginToken(otherStaff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + loginToken(otherStaff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Unauthorized note\"}"))
                .andExpect(status().isForbidden());

        for (String nextStatus : new String[] {"ASSIGNED", "IN_PROGRESS"}) {
            mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + nextStatus + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(nextStatus));
        }

        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("No image attached"))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Upload a resolution-proof image before resolving."));

        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("Repair completed and tested."))
                        .file(new MockMultipartFile("image", "proof.svg", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("Repair completed and tested."))
                        .file(new MockMultipartFile("image", "proof.png", "image/png",
                                new byte[(int) ComplaintImageStorage.MAX_IMAGE_BYTES + 1]))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isPayloadTooLarge());

        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("Unauthorized resolution."))
                        .file(new MockMultipartFile("image", "proof.png", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + loginToken(otherStaff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("Unauthorized resolution."))
                        .file(new MockMultipartFile("image", "proof.png", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                        .part(resolutionPart("Unauthorized resolution."))
                        .file(new MockMultipartFile("image", "proof.png", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + roleToken(UserRole.FACULTY)))
                .andExpect(status().isForbidden());
        assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.IN_PROGRESS);
        assertThat(resolutionProofRepository.findByComplaint_Id(complaintId)).isEmpty();

        String proofReference = null;
        try {
            mockMvc.perform(multipart("/api/complaints/{id}/resolution", complaintId)
                            .part(resolutionPart("Repair completed and tested."))
                            .file(new MockMultipartFile("image", "proof.png", "image/png", pngImage()))
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.resolutionProof.staffName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.resolutionProof.uploadedAt").isNotEmpty())
                    .andExpect(jsonPath("$.resolutionProof.note").value("Repair completed and tested."))
                    .andExpect(jsonPath("$.resolutionProof.imageUrl")
                            .value("/api/complaints/" + complaintId + "/resolution-proof"))
                    .andExpect(jsonPath("$.statusHistory[3].previousStatus").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.statusHistory[3].status").value("RESOLVED"))
                    .andExpect(jsonPath("$.statusHistory[3].actorName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.statusHistory[3].note").value("Repair completed and tested."));
            proofReference = resolutionProofRepository.findByComplaint_Id(complaintId)
                    .orElseThrow().getImageReference();

            assertThat(Files.exists(Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(proofReference))).isTrue();
            assertThat(new ComplaintImageStorage(uploadDirectory).read(proofReference).bytes())
                    .containsExactly(pngImage());
            assertThat(complaintRepository.findById(complaintId).orElseThrow().getStatus())
                    .isEqualTo(ComplaintStatus.RESOLVED);
            assertThat(complaintRepository.findById(complaintId).orElseThrow().getImageReference())
                    .isEqualTo(originalImageReference);
            assertThat(auditEntryRepository.findAll().stream()
                    .filter(entry -> entry.getComplaintId() != null && complaintId == entry.getComplaintId())
                    .map(AuditEntry::getActionType))
                    .containsExactlyInAnyOrder(
                            "COMPLAINT_SUBMITTED",
                            "COMPLAINT_IMAGE_UPLOADED",
                            "COMPLAINT_ASSIGNED",
                            "WORK_NOTE_ADDED",
                            "COMPLAINT_STATUS_CHANGED",
                            "COMPLAINT_STATUS_CHANGED",
                            "RESOLUTION_PROOF_UPLOADED",
                            "COMPLAINT_RESOLVED");
            mockMvc.perform(get("/api/complaints/{id}/image", complaintId)
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                            .isEqualTo("image/png"));
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + loginToken(otherStaff)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + registerStudentToken()))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolutionProof.staffName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.resolutionProof.imageUrl")
                            .value("/api/complaints/" + complaintId + "/resolution-proof"))
                    .andExpect(jsonPath("$.resolutionProof.uploadedAt").isNotEmpty())
                    .andExpect(jsonPath("$.workNotes.length()").value(0));
            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolutionProof.staffName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.resolutionProof.imageUrl")
                            .value("/api/complaints/" + complaintId + "/resolution-proof"));
            mockMvc.perform(get("/api/complaints/assigned")
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("RESOLVED"));
            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.workNotes.length()").value(1))
                    .andExpect(jsonPath("$.statusHistory.length()").value(4))
                    .andExpect(jsonPath("$.status").value("RESOLVED"));
            String reloggedStaffToken = loginToken(staff);
            mockMvc.perform(get("/api/complaints/{id}", complaintId)
                            .header("Authorization", "Bearer " + reloggedStaffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolutionProof.imageUrl")
                            .value("/api/complaints/" + complaintId + "/resolution-proof"));
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaintId)
                            .header("Authorization", "Bearer " + reloggedStaffToken))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                            .isEqualTo("image/png"));
        } finally {
            imageStorage.delete(proofReference);
        }

        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());
        assertThat(statusHistoryRepository.findByComplaint_IdOrderByChangedAtAsc(complaintId))
                .extracting(ComplaintStatusHistory::getNewStatus)
                .containsExactly(
                        ComplaintStatus.SUBMITTED,
                        ComplaintStatus.ASSIGNED,
                        ComplaintStatus.IN_PROGRESS,
                        ComplaintStatus.RESOLVED);
        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"note\":\"Resolution reviewed and closed.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.statusHistory[4].actorName").isNotEmpty())
                .andExpect(jsonPath("$.statusHistory[4].note").value("Resolution reviewed and closed."));
        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workNotes.length()").value(0));
        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Unauthorized note\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + roleToken(UserRole.FACULTY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Unauthorized note\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + roleToken(UserRole.FACULTY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facultyOwnerCanViewResolutionProofButOtherFacultyCannot() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Faculty proof " + UUID.randomUUID(), "Test department"));
        User faculty = roleUser(UserRole.FACULTY, department);
        User otherFaculty = roleUser(UserRole.FACULTY, department);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Faculty proof " + UUID.randomUUID(), "Test category"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Faculty proof building " + UUID.randomUUID(), null, null));
        String proofImageReference = imageStorage.store(
                new MockMultipartFile("image", "proof.png", "image/png", pngImage()));
        try {
            Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                    faculty, category, location, department, "Faculty complaint", "Authorized faculty complaint",
                    "CMP-" + UUID.randomUUID(), ComplaintPriority.MEDIUM, null));
            complaint.updateStatus(ComplaintStatus.RESOLVED);
            complaintRepository.saveAndFlush(complaint);
            User staff = roleUser(UserRole.STAFF, department);
            assignmentRepository.saveAndFlush(new ComplaintAssignment(complaint, staff, null, "Faculty test assignment"));
            resolutionProofRepository.saveAndFlush(
                    new ComplaintResolutionProof(complaint, staff, proofImageReference));
            workNoteRepository.saveAndFlush(new ComplaintWorkNote(complaint, staff, "Repair completed."));

            String facultyToken = loginToken(faculty);
            mockMvc.perform(get("/api/complaints/{id}", complaint.getId())
                            .header("Authorization", "Bearer " + facultyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolutionProof.staffName").isNotEmpty())
                    .andExpect(jsonPath("$.resolutionProof.uploadedAt").isNotEmpty())
                    .andExpect(jsonPath("$.resolutionProof.imageUrl")
                            .value("/api/complaints/" + complaint.getId() + "/resolution-proof"))
                    .andExpect(jsonPath("$.currentAssignment.staffName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.currentAssignment.assignedAt").isNotEmpty())
                    .andExpect(jsonPath("$.workNotes[0].authorName").value(staff.getFullName()))
                    .andExpect(jsonPath("$.workNotes[0].note").value("Repair completed."));
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaint.getId())
                            .header("Authorization", "Bearer " + facultyToken))
                    .andExpect(status().isOk())
                    .andExpect(resultImage -> assertThat(resultImage.getResponse().getContentType())
                            .isEqualTo("image/png"));

            String otherFacultyToken = loginToken(otherFaculty);
            mockMvc.perform(get("/api/complaints/{id}", complaint.getId())
                            .header("Authorization", "Bearer " + otherFacultyToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/complaints/{id}/resolution-proof", complaint.getId())
                            .header("Authorization", "Bearer " + otherFacultyToken))
                    .andExpect(status().isForbidden());
        } finally {
            imageStorage.delete(proofImageReference);
        }
    }

    @Test
    void complaintCommentsAndResolutionNotesUseExistingAccessRulesAndRemainSeparate() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Comment workflow " + UUID.randomUUID(), "Test department"));
        User student = roleUser(UserRole.STUDENT, department);
        User otherStudent = roleUser(UserRole.STUDENT, department);
        User faculty = roleUser(UserRole.FACULTY, department);
        User staff = roleUser(UserRole.STAFF, department);
        User otherStaff = roleUser(UserRole.STAFF, department);
        User admin = roleUser(UserRole.ADMIN, null);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Comment workflow " + UUID.randomUUID(), "Test category"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Comment workflow " + UUID.randomUUID(), null, null));
        Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, department, "Comment workflow complaint", "Complaint details",
                "CMP-" + UUID.randomUUID(), ComplaintPriority.MEDIUM, null));
        assignmentRepository.saveAndFlush(new ComplaintAssignment(complaint, staff, admin, null));
        long complaintId = complaint.getId();
        String studentToken = loginToken(student);
        String staffToken = loginToken(staff);
        String otherStaffToken = loginToken(otherStaff);
        String facultyToken = loginToken(faculty);
        String adminToken = loginToken(admin);
        String commentRequestId = UUID.randomUUID().toString();

        MvcResult studentCommentResult = mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  The light is still flickering.  \","
                                + "\"authorId\":999,\"authorRole\":\"ADMIN\","
                                + "\"requestId\":\"" + commentRequestId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value(student.getFullName()))
                .andExpect(jsonPath("$.authorRole").value("STUDENT"))
                .andExpect(jsonPath("$.content").value("The light is still flickering."))
                .andExpect(jsonPath("$.type").value("COMMENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        long studentCommentId = objectMapper.readTree(studentCommentResult.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  The light is still flickering.  \","
                                + "\"requestId\":\"" + commentRequestId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(studentCommentId));
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Different content.\","
                                + "\"requestId\":\"" + commentRequestId + "\"}"))
                .andExpect(status().isConflict());
        assertUnreadNotifications(studentToken, 0);
        assertUnreadNotifications(staffToken, 1);
        assertUnreadNotifications(adminToken, 1);
        assertUnreadNotifications(facultyToken, 1);
        assertUnreadNotifications(otherStaffToken, 0);
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("COMPLAINT_COMMENT"))
                .andExpect(jsonPath("$.content[0].complaintId").value(complaintId))
                .andExpect(jsonPath("$.content[0].message").value(
                        "A new comment was added to assigned complaint " + complaint.getComplaintCode() + "."));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].complaintId").value(complaintId));
        assertNotificationDoesNotContain(admin, "The light is still flickering.");
        notificationService.commentAdded(
                complaint,
                workNoteRepository.findByComplaint_IdAndRecordTypeOrderByCreatedAtAscIdAsc(
                        complaintId, ComplaintNoteType.COMMENT).getFirst());
        assertUnreadNotifications(staffToken, 1);
        assertUnreadNotifications(adminToken, 1);
        assertUnreadNotifications(facultyToken, 1);
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        assertUnreadNotifications(staffToken, 1);
        assertUnreadNotifications(adminToken, 1);
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("content", "x".repeat(1001)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Staff update.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("STAFF"));
        assertUnreadNotifications(studentToken, 1);
        assertUnreadNotifications(staffToken, 1);
        assertUnreadNotifications(adminToken, 2);
        assertUnreadNotifications(facultyToken, 2);
        assertNotificationDoesNotContain(student, "Staff update.");
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Admin update.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("ADMIN"));
        assertUnreadNotifications(studentToken, 2);
        assertUnreadNotifications(staffToken, 2);
        assertUnreadNotifications(adminToken, 2);
        assertUnreadNotifications(facultyToken, 3);
        assertNotificationDoesNotContain(student, "Admin update.");

        mockMvc.perform(post("/api/complaints/{id}/work-notes", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Internal inspection record.\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].authorRole").value("STUDENT"));
        mockMvc.perform(get("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/complaints/{id}", complaintId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.workNotes.length()").value(1));

        mockMvc.perform(post("/api/complaints/{id}/resolution-notes", complaintId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Repair parts are on order.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("STAFF"))
                .andExpect(jsonPath("$.type").value("RESOLUTION_NOTE"));
        mockMvc.perform(post("/api/complaints/{id}/resolution-notes", complaintId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Follow up after repair.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("ADMIN"));
        mockMvc.perform(get("/api/complaints/{id}/resolution-notes", complaintId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/complaints/{id}/resolution-notes", complaintId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + loginToken(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not my complaint.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + otherStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not assigned.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Faculty remains read-only.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/complaints/{id}/resolution-notes", complaintId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not permitted.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}/comments", complaintId)
                        .header("Authorization", "Bearer " + loginToken(otherStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}/comments", complaintId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void facultyDashboardReadsCampusStudentComplaintsAndRemainsReadOnly() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                new Department("Faculty dashboard " + UUID.randomUUID(), "Test department"));
        User faculty = roleUser(UserRole.FACULTY, department);
        User otherFaculty = roleUser(UserRole.FACULTY, department);
        User staff = roleUser(UserRole.STAFF, department);
        User student = roleUser(UserRole.STUDENT, department);
        User otherStudent = roleUser(UserRole.STUDENT, department);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Faculty dashboard " + UUID.randomUUID(), "Test category"));
        CampusLocation location = locationRepository.saveAndFlush(
                new CampusLocation("Faculty dashboard " + UUID.randomUUID(), null, null));
        Map<ComplaintStatus, Long> initialStatusCounts = complaintRepository
                .countCampusStudentComplaintsByStatus(UserRole.STUDENT).stream()
                .collect(Collectors.toMap(row -> (ComplaintStatus) row[0], row -> ((Number) row[1]).longValue()));
        long initialTotal = initialStatusCounts.values().stream().mapToLong(Long::longValue).sum();
        long expectedTotal = initialTotal + 3;
        long expectedResolved = initialStatusCounts.getOrDefault(ComplaintStatus.RESOLVED, 0L)
                + initialStatusCounts.getOrDefault(ComplaintStatus.CLOSED, 0L) + 1;
        long expectedPending = initialStatusCounts.getOrDefault(ComplaintStatus.SUBMITTED, 0L)
                + initialStatusCounts.getOrDefault(ComplaintStatus.UNDER_REVIEW, 0L) + 1;

        Complaint pending = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, department, "=HYPERLINK(\"https://invalid.test\")",
                "Student-reported wifi is unavailable.", "CMP-" + UUID.randomUUID(), ComplaintPriority.URGENT, null));
        Complaint assigned = complaintRepository.saveAndFlush(new Complaint(
                otherStudent, category, location, department, "Assigned Student report",
                "A second Student report", "CMP-" + UUID.randomUUID(), ComplaintPriority.MEDIUM, null));
        assigned.updateStatus(ComplaintStatus.ASSIGNED);
        complaintRepository.saveAndFlush(assigned);
        assignmentRepository.saveAndFlush(new ComplaintAssignment(assigned, staff, null, "Test assignment"));

        Complaint resolved = complaintRepository.saveAndFlush(new Complaint(
                student, category, location, department, "Resolved Student report",
                "A resolved Student report", "CMP-" + UUID.randomUUID(), ComplaintPriority.HIGH, null));
        resolved.updateStatus(ComplaintStatus.RESOLVED);
        complaintRepository.saveAndFlush(resolved);
        statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                resolved, staff, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, "Test resolution"));

        Complaint privateComplaint = complaintRepository.saveAndFlush(new Complaint(
                otherFaculty, category, location, department, "Faculty-owned report",
                "Faculty-created report remains outside the campus Student list.",
                "CMP-" + UUID.randomUUID(), ComplaintPriority.MEDIUM, null));
        String facultyToken = loginToken(faculty);
        String studentToken = loginToken(student);
        String staffToken = loginToken(staff);

        mockMvc.perform(get("/api/faculty/summary")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(expectedTotal))
                .andExpect(jsonPath("$.pending").value(expectedPending))
                .andExpect(jsonPath("$.assigned").value(initialStatusCounts.getOrDefault(ComplaintStatus.ASSIGNED, 0L) + 1))
                .andExpect(jsonPath("$.inProgress").value(initialStatusCounts.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L)))
                .andExpect(jsonPath("$.resolved").value(expectedResolved))
                .andExpect(jsonPath("$.resolutionRate")
                        .value(expectedTotal == 0 ? 0 : (expectedResolved * 100.0) / expectedTotal));

        mockMvc.perform(get("/api/faculty/complaints")
                        .param("page", "0").param("size", "1")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(expectedTotal))
                .andExpect(jsonPath("$.totalPages").value(expectedTotal));

        MvcResult facultyPage = mockMvc.perform(get("/api/faculty/complaints")
                        .param("page", "0").param("size", "100")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expectedTotal))
                .andReturn();
        JsonNode facultyRows = objectMapper.readTree(facultyPage.getResponse().getContentAsString()).get("content");
        JsonNode pendingRow = findByComplaintId(facultyRows, pending.getId());
        assertThat(pendingRow.get("title").asText()).isEqualTo("=HYPERLINK(\"https://invalid.test\")");
        assertThat(pendingRow.get("description").asText()).isEqualTo("Student-reported wifi is unavailable.");
        assertThat(pendingRow.get("reporterName").asText()).isEqualTo(student.getFullName());
        assertThat(findByComplaintId(facultyRows, assigned.getId()).get("assignedStaff").get(0).asText())
                .isEqualTo(staff.getFullName());
        assertThat(findByComplaintId(facultyRows, resolved.getId()).get("status").asText()).isEqualTo("RESOLVED");
        assertThat(facultyRows.findValues("complaintId"))
                .extracting(JsonNode::asText)
                .doesNotContain(privateComplaint.getComplaintCode());

        mockMvc.perform(get("/api/faculty/complaints")
                        .param("search", "HYPERLINK")
                        .param("status", "SUBMITTED")
                        .param("priority", "URGENT")
                        .param("categoryId", category.getId().toString())
                        .param("departmentId", department.getId().toString())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("=HYPERLINK(\"https://invalid.test\")"));

        mockMvc.perform(get("/api/faculty/complaints")
                        .param("sort", "reporter.passwordHash")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/faculty/complaints")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/faculty/complaints")
                        .param("from", "2026-12-31").param("to", "2026-01-01")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/faculty/analytics")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus[?(@.label == 'RESOLVED')].count")
                        .value(Math.toIntExact(initialStatusCounts.getOrDefault(ComplaintStatus.RESOLVED, 0L) + 1)))
                .andExpect(jsonPath("$.byCategory[?(@.label == '" + category.getName() + "')].count").value(3))
                .andExpect(jsonPath("$.monthlySubmissions.length()").value(12));

        MvcResult csv = mockMvc.perform(get("/api/faculty/reports.csv")
                        .param("search", "HYPERLINK")
                        .param("status", "SUBMITTED")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).startsWith("text/csv"))
                .andReturn();
        assertThat(csv.getResponse().getContentAsString())
                .contains("'=HYPERLINK", student.getFullName(), "Student-reported wifi is unavailable.")
                .doesNotContain("Faculty-owned report");

        mockMvc.perform(get("/api/faculty/complaints"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/faculty/summary")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/faculty/analytics")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/faculty/complaints")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}", pending.getId())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporterName").value(student.getFullName()))
                .andExpect(jsonPath("$.description").value("Student-reported wifi is unavailable."));
        mockMvc.perform(get("/api/complaints/{id}", privateComplaint.getId())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/complaints/{id}/status", pending.getId())
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", pending.getId())
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + department.getId() + ",\"staffId\":" + staff.getId() + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}", privateComplaint.getId())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void facultyCanSubmitAndViewOnlyTheirOwnComplaintsSeparatelyFromStudentCampusList() throws Exception {
        Department studentDepartment = departmentRepository.saveAndFlush(
                new Department("Student complaint " + UUID.randomUUID(), "Student department"));
        Department facultyDepartment = departmentRepository.saveAndFlush(
                new Department("Faculty complaint " + UUID.randomUUID(), "Faculty department"));
        User faculty = roleUser(UserRole.FACULTY, facultyDepartment);
        User otherFaculty = roleUser(UserRole.FACULTY, facultyDepartment);
        User student = roleUser(UserRole.STUDENT, studentDepartment);
        User staff = roleUser(UserRole.STAFF, studentDepartment);
        User admin = roleUser(UserRole.ADMIN, null);
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Faculty submission " + UUID.randomUUID(), "Test category"));
        String facultyToken = loginToken(faculty);
        String otherFacultyToken = loginToken(otherFaculty);
        String studentToken = loginToken(student);
        String staffToken = loginToken(staff);
        String adminToken = loginToken(admin);

        MvcResult studentSubmission = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Student campus report",
                                "Submitted from a different department.", "Library", "HIGH"))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        JsonNode studentResponse = objectMapper.readTree(studentSubmission.getResponse().getContentAsString());
        Complaint studentComplaint = complaintRepository.findById(studentResponse.get("id").asLong()).orElseThrow();
        assertThat(studentComplaint.getReporter().getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(studentComplaint.getReporter().getId()).isEqualTo(student.getId());
        assertThat(studentComplaint.getDepartment().getId()).isEqualTo(studentDepartment.getId());

        MvcResult campusList = mockMvc.perform(get("/api/faculty/complaints")
                        .param("page", "0").param("size", "100")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode campusRows = objectMapper.readTree(campusList.getResponse().getContentAsString()).get("content");
        assertThat(campusRows.findValues("complaintId")).extracting(JsonNode::asText)
                .contains(studentResponse.get("complaintId").asText());
        mockMvc.perform(get("/api/complaints/my")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(put("/api/admin/complaints/{id}/assignment", studentComplaint.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + studentDepartment.getId()
                                + ",\"staffId\":" + staff.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/complaints/{id}/status", studentComplaint.getId())
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/complaints/{id}/status", studentComplaint.getId())
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/faculty/complaints")
                        .param("page", "0").param("size", "100").param("status", "IN_PROGRESS")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].complaintId").value(studentResponse.get("complaintId").asText()))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));

        MvcResult facultySubmission = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Faculty personal report",
                                "Submitted by the authenticated Faculty account.", "Science Building", "MEDIUM"))
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        JsonNode facultyResponse = objectMapper.readTree(facultySubmission.getResponse().getContentAsString());
        Complaint facultyComplaint = complaintRepository.findById(facultyResponse.get("id").asLong()).orElseThrow();
        assertThat(facultyComplaint.getReporter().getRole()).isEqualTo(UserRole.FACULTY);
        assertThat(facultyComplaint.getReporter().getId()).isEqualTo(faculty.getId());
        assertThat(facultyComplaint.getDepartment().getId()).isEqualTo(facultyDepartment.getId());

        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(facultyComplaint.getId()))
                .andExpect(jsonPath("$[0].complaintId").value(facultyResponse.get("complaintId").asText()))
                .andExpect(jsonPath("$[0].title").value("Faculty personal report"))
                .andExpect(jsonPath("$[0].category").value(category.getName()))
                .andExpect(jsonPath("$[0].priority").value("MEDIUM"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        campusList = mockMvc.perform(get("/api/faculty/complaints")
                        .param("page", "0").param("size", "100")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andReturn();
        campusRows = objectMapper.readTree(campusList.getResponse().getContentAsString()).get("content");
        assertThat(campusRows.findValues("complaintId")).extracting(JsonNode::asText)
                .contains(studentResponse.get("complaintId").asText())
                .doesNotContain(facultyResponse.get("complaintId").asText());

        MvcResult adminList = mockMvc.perform(get("/api/complaints")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode adminRows = objectMapper.readTree(adminList.getResponse().getContentAsString());
        assertThat(adminRows.findValues("complaintId")).extracting(JsonNode::asText)
                .contains(facultyResponse.get("complaintId").asText());

        mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Unauthenticated", "No token", "Library", "LOW")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Staff attempt", "Not permitted", "Library", "LOW"))
                        .header("Authorization", "Bearer " + loginToken(roleUser(UserRole.STAFF, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/complaints/{id}/status", studentComplaint.getId())
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/complaints/{id}", studentComplaint.getId())
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void submissionRequiresStudentRegistrationDetails() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"name\":\"Incomplete Student\",\"email\":\"incomplete@example.test\","
                                + "\"password\":\"ComplaintTest9876\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidImageAndMissingAuthenticationAreRejected() throws Exception {
        ComplaintCategory category = categoryRepository.saveAndFlush(
                new ComplaintCategory("Test Water", "Test category"));
        mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Water issue", "No water", "Block B", "MEDIUM")))
                .andExpect(status().isUnauthorized());

        String token = registerStudentToken();
        mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(category.getId(), "Invalid attachment", "Description", "Block B", "LOW"))
                        .file(new MockMultipartFile("image", "bad.png", "image/png", "<html>not an image</html>".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void validWebpImageIsStoredAndReadWithDetectedContentType() throws Exception {
        byte[] content = webpImage();
        String reference = imageStorage.store(
                new MockMultipartFile("image", "campus.webp", "image/webp", content));
        try {
            StoredComplaintImage stored = imageStorage.read(reference);
            assertThat(stored.contentType()).isEqualTo("image/webp");
            assertThat(stored.bytes()).isEqualTo(content);
        } finally {
            imageStorage.delete(reference);
        }
    }

    private String registerStudentToken() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String unique = UUID.randomUUID().toString();
        Department department = departmentRepository.saveAndFlush(
                new Department("Complaint Test " + unique, "Test department"));
        MvcResult response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"name\":\"Complaint Test Student\",\"email\":\"" + email
                                + "\",\"password\":\"ComplaintTest9876\",\"rollNumber\":\"" + unique
                                + "\",\"className\":\"Test Class\",\"section\":\"A\",\"departmentId\":"
                                + department.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private long submitComplaint(String token, Long categoryId, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(categoryId, title, "Admin overview test", "Test Hall", "MEDIUM"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long submitComplaintWithImage(String token, Long categoryId, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/complaints")
                        .part(complaintPart(categoryId, title, "Staff workflow test", "Test Hall", "MEDIUM"))
                        .file(new MockMultipartFile("image", "original.png", "image/png", pngImage()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode findByComplaintId(JsonNode complaints, long id) {
        for (JsonNode complaint : complaints) {
            if (complaint.get("id").asLong() == id) {
                return complaint;
            }
        }
        throw new AssertionError("Expected complaint was not included in the authorized complaint list.");
    }

    private String roleToken(UserRole role) throws Exception {
        return loginToken(roleUser(role, null));
    }

    private User roleUser(UserRole role, Department department) {
        String unique = UUID.randomUUID().toString();
        String email = role.name().toLowerCase() + "." + unique + "@example.test";
        User user = new User("Complaint Test " + role + " " + unique.substring(0, 8), email, role, department);
        user.setPasswordHash(passwordEncoder.encode("ComplaintTest9876"));
        return userRepository.saveAndFlush(user);
    }

    private String loginToken(User user) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"ComplaintTest9876\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void assertUnreadNotifications(String token, long expectedCount) throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(expectedCount));
    }

    private void assertNotificationDoesNotContain(User recipient, String privateContent) {
        var notifications = notificationRepository.findByRecipient_Id(
                recipient.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .allSatisfy(notification -> assertThat(notification.getMessage()).doesNotContain(privateContent));
    }

    private MockPart complaintPart(Long categoryId, String title, String description, String location, String priority) {
        MockPart part = new MockPart("complaint", (
                "{\"title\":\"" + title + "\",\"description\":\"" + description
                        + "\",\"categoryId\":" + categoryId + ",\"location\":\"" + location
                        + "\",\"priority\":\"" + priority + "\"}").getBytes());
        part.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return part;
    }

    private MockPart resolutionPart(String note) {
        MockPart part = new MockPart("resolution", ("{\"note\":\"" + note + "\"}").getBytes());
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] webpImage() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "webp", output)).isTrue();
        return output.toByteArray();
    }
}
