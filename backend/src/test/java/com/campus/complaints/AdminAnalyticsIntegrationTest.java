package com.campus.complaints;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsIntegrationTest {

    private static final String PASSWORD = "AnalyticsTest123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(UserRole role) {
        String unique = UUID.randomUUID().toString();
        User user = new User("Analytics " + role, role.name().toLowerCase() + "." + unique + "@example.test",
                role, null);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    @Test
    void analyticsEndpointRequiresAdminRole() throws Exception {
        // Unauthenticated request should fail with 401
        mockMvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isUnauthorized());

        // Authenticated as student should fail with 403
        User student = createUser(UserRole.STUDENT);
        String studentToken = jwtService.createAccessToken(student);

        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAnalyticsAndExports() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String adminToken = jwtService.createAccessToken(admin);

        // Check Analytics JSON
        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.total").isNumber())
                .andExpect(jsonPath("$.byDepartment").isArray())
                .andExpect(jsonPath("$.byCategory").isArray())
                .andExpect(jsonPath("$.byPriority").isArray())
                .andExpect(jsonPath("$.byStatus").isArray())
                .andExpect(jsonPath("$.monthlyTrends").isArray())
                .andExpect(jsonPath("$.staffWorkload").isArray())
                .andExpect(jsonPath("$.filterOptions").exists());

        // Check Analytics CSV export
        mockMvc.perform(get("/api/admin/analytics/export.csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString();
                    assertThat(csv).contains("Total Complaints");
                    assertThat(csv).contains("Metric,Value");
                });

        // Check SLA CSV export
        mockMvc.perform(get("/api/admin/sla/export.csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString();
                    assertThat(csv).contains("Complaint ID,Title,Priority,Status");
                });

        // Check Audit Trail CSV export
        mockMvc.perform(get("/api/admin/audit/export.csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString();
                    assertThat(csv).contains("Audit ID,Timestamp,Actor Name");
                });

        // Check Complaints list CSV export
        mockMvc.perform(get("/api/complaints/export.csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString();
                    assertThat(csv).contains("Complaint ID,Title,Status,Priority");
                });
    }
}
