package com.campus.complaints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    private static final String PASSWORD = "CampusSecure123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long departmentId;

    @BeforeEach
    void createDepartmentForRegistration() {
        departmentId = departmentRepository.save(
                new Department("Test Department " + UUID.randomUUID(), "Authentication test department")).getId();
    }

    @Test
    void publicRegistrationAlwaysCreatesStudentAndStoresBcryptHash() throws Exception {
        String email = uniqueEmail();
        String rollNumber = "000A" + UUID.randomUUID().toString().substring(0, 8);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("ADMIN", email, rollNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(PASSWORD, "passwordHash", "password_hash");
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(created.getRollNumber()).isEqualTo(rollNumber);
        assertThat(created.getDepartment().getId()).isEqualTo(departmentId);
        assertThat(created.getClassName()).isNull();
        assertThat(created.getSection()).isNull();
        assertThat(created.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();

        String token = loginAndGetToken(email, PASSWORD);
        mockMvc.perform(get("/api/complaints/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateRollNumberIsRejectedAndLeadingZerosArePreserved() throws Exception {
        String firstEmail = uniqueEmail();
        String rollNumber = "000A" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(null, firstEmail, rollNumber)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(null, uniqueEmail(), rollNumber)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this roll number already exists."));

        assertThat(userRepository.findByEmail(firstEmail).orElseThrow().getRollNumber()).isEqualTo(rollNumber);
    }

    @Test
    void departmentSetupIsAdminOnlyAndCreatesUsableRegistrationOption() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized Department\"}"))
                .andExpect(status().isUnauthorized());

        String adminEmail = uniqueEmail();
        User admin = new User("Test Department Admin", adminEmail, UserRole.ADMIN, null);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);
        String adminToken = loginAndGetToken(adminEmail, PASSWORD);
        String departmentName = "Created Department " + UUID.randomUUID();

        String created = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + departmentName + "\",\"description\":\"Test department\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(departmentName))
                .andReturn().getResponse().getContentAsString();
        long createdId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/auth/departments"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString(departmentName)));
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + departmentName + "\"}"))
                .andExpect(status().isConflict());

        String renamedDepartment = departmentName + " Updated";
        mockMvc.perform(put("/api/admin/departments/" + createdId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + renamedDepartment + "\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamedDepartment));
        mockMvc.perform(patch("/api/admin/departments/" + createdId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertThat(departmentRepository.findById(createdId).orElseThrow().isActive()).isFalse();
        mockMvc.perform(patch("/api/admin/departments/" + createdId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(departmentRepository.findById(createdId)).isPresent();
    }

    @Test
    void adminCanUpdateAndDeactivateNonAdminAccountsWithoutChangingRoles() throws Exception {
        String adminEmail = uniqueEmail();
        User admin = new User("Management Admin", adminEmail, UserRole.ADMIN, null);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin = userRepository.save(admin);
        String adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String studentEmail = registerStudent();
        User student = userRepository.findByEmail(studentEmail).orElseThrow();
        String updatedEmail = uniqueEmail();
        String updatedRoll = "E" + UUID.randomUUID().toString().substring(0, 8);
        String updateJson = "{\"name\":\"Updated Student\",\"email\":\"" + updatedEmail
                + "\",\"departmentId\":" + departmentId
                + ",\"rollNumber\":\"" + updatedRoll
                + "\",\"className\":\"BSc Computing\",\"section\":\"B\"}";

        mockMvc.perform(put("/api/admin/users/" + student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/users/" + student.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Student"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.rollNumber").value(updatedRoll));

        String secondStudentEmail = registerStudent();
        User secondStudent = userRepository.findByEmail(secondStudentEmail).orElseThrow();
        mockMvc.perform(put("/api/admin/users/" + student.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicate Email\",\"email\":\"" + secondStudentEmail
                                + "\",\"departmentId\":" + departmentId
                                + ",\"rollNumber\":\"" + updatedRoll
                                + "\",\"className\":\"BSc Computing\",\"section\":\"B\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/users/" + secondStudent.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicate Roll\",\"email\":\"" + uniqueEmail()
                                + "\",\"departmentId\":" + departmentId
                                + ",\"rollNumber\":\"" + updatedRoll
                                + "\",\"className\":\"BSc Computing\",\"section\":\"B\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/admin/users/" + student.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        User updatedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertThat(updatedStudent.getEmail()).isEqualTo(updatedEmail);
        assertThat(updatedStudent.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(updatedStudent.isActive()).isFalse();
        mockMvc.perform(patch("/api/admin/users/" + student.getId() + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void publicRegistrationCannotChooseFacultyOrStaffRoles() throws Exception {
        for (String role : new String[]{"ADMIN", "FACULTY", "STAFF"}) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson(role, uniqueEmail())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.role").value("STUDENT"));
        }
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(null, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(null, email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void loginAcceptsCorrectPasswordAndRejectsIncorrectPassword() throws Exception {
        String email = registerStudent();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void loginIssuesProfileAndJwtForAdminStaffAndFacultyRoles() throws Exception {
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.STAFF, UserRole.FACULTY}) {
            String email = uniqueEmail();
            User account = new User(role.name() + " Login Test", email, role, null);
            account.setPasswordHash(passwordEncoder.encode(PASSWORD));
            userRepository.save(account);

            String response = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(email, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.role").value(role.name()))
                    .andExpect(jsonPath("$.user.active").value(true))
                    .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            String token = objectMapper.readTree(response).get("accessToken").asText();
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));
        }
    }

    @Test
    void validJwtAuthenticatesProfileAndInvalidOrMissingTokensAreRejected() throws Exception {
        String email = registerStudent();
        String token = loginAndGetToken(email, PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleRestrictionsProtectAdminAndProvisioningEndpoints() throws Exception {
        String studentEmail = registerStudent();
        String studentToken = loginAndGetToken(studentEmail, PASSWORD);
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/faculty/private").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/private").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionJson("STAFF", uniqueEmail())))
                .andExpect(status().isUnauthorized());

        String adminEmail = uniqueEmail();
        User admin = new User("Test Admin", adminEmail, UserRole.ADMIN, null);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);
        String adminToken = loginAndGetToken(adminEmail, PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String staffEmail = uniqueEmail();
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionJson("STAFF", staffEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(userRepository.findByEmail(staffEmail).orElseThrow().getRole()).isEqualTo(UserRole.STAFF);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionJson("ADMIN", uniqueEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facultyAnalyticsRequiresAnUnexpiredFacultyToken() throws Exception {
        mockMvc.perform(get("/api/faculty/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mockMvc.perform(get("/api/faculty/analytics")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());

        String facultyEmail = createRoleAccount(UserRole.FACULTY);
        String facultyToken = loginAndGetToken(facultyEmail, PASSWORD);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FACULTY"));
        mockMvc.perform(get("/api/faculty/analytics").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus").isArray())
                .andExpect(jsonPath("$.averageResolutionHours").value(org.hamcrest.Matchers.nullValue()));

        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.STAFF, UserRole.ADMIN}) {
            String email = role == UserRole.STUDENT ? registerStudent() : createRoleAccount(role);
            String token = loginAndGetToken(email, PASSWORD);
            mockMvc.perform(get("/api/faculty/analytics").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        String expiredToken = Jwts.builder()
                .subject(facultyEmail)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        "VGhpc0lzQVN0cm9uZ1Rlc3RTZWNyZXRGb3JUZXN0T25seS0zMkJ5dGVz")))
                .compact();
        mockMvc.perform(get("/api/faculty/analytics")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingHealthEndpointsRemainPublicAndDatabaseHealthWorks() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String registerStudent() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(null, email)))
                .andExpect(status().isCreated());
        return email;
    }

    private String createRoleAccount(UserRole role) {
        String email = uniqueEmail();
        User account = new User(role.name() + " Test", email, role, null);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(account);
        return email;
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private String registrationJson(String role, String email) {
        String rollNumber = "R" + email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9]", "");
        return registrationJson(role, email, rollNumber);
    }

    private String registrationJson(String role, String email, String rollNumber) {
        String roleProperty = role == null ? "" : ",\"role\":\"" + role + "\"";
        return "{\"name\":\"Test Student\",\"email\":\"" + email
                + "\",\"password\":\"" + PASSWORD
                + "\",\"rollNumber\":\"" + rollNumber + "\""
                + ",\"departmentId\":" + departmentId + roleProperty + "}";
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String provisionJson(String role, String email) {
        return "{\"name\":\"Provisioned User\",\"email\":\"" + email
                + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}";
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
