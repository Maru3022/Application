package com.healthlife.user;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.user.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc controller tests for UserController covering:
 * - GET /me returns profile
 * - PUT /me updates profile
 * - DELETE /me soft-deletes
 * - GET /me/goals returns goals
 * - PUT /me/goals updates goals
 * - GET /me/subscription returns plan
 * - GET /me/data-export returns GDPR export
 * - 401 when no JWT
 * - 404 when profile not found
 */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserService userService;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "user@test.com", "USER");
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    void getProfile_withJwt_shouldReturn200() throws Exception {
        when(userService.getProfile())
                .thenReturn(UserProfileResponse.builder()
                        .id(UUID.randomUUID())
                        .email("user@test.com")
                        .displayName("Test")
                        .build());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void getProfile_withoutJwt_shouldReturn401or403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().is4xxClientError());
    }

    @Test
    void getProfile_notFound_shouldReturn404() throws Exception {
        when(userService.getProfile()).thenThrow(new ResourceNotFoundException("Profile", "userId", "x"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    void updateProfile_shouldReturn200() throws Exception {
        when(userService.updateProfile(any()))
                .thenReturn(UserProfileResponse.builder()
                        .id(UUID.randomUUID())
                        .displayName("Updated")
                        .build());

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"displayName":"Updated","timezone":"UTC"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated"));
    }

    // ── DELETE /api/v1/users/me ───────────────────────────────────────────────

    @Test
    void deleteAccount_shouldReturn204() throws Exception {
        doNothing().when(userService).deleteAccount();

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/v1/users/me/goals ────────────────────────────────────────────

    @Test
    void getGoals_shouldReturn200() throws Exception {
        when(userService.getGoals())
                .thenReturn(
                        UserGoalsDto.builder().dailySteps(10000).waterMl(2500).build());

        mockMvc.perform(get("/api/v1/users/me/goals").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailySteps").value(10000));
    }

    @Test
    void getGoals_notFound_shouldReturn404() throws Exception {
        when(userService.getGoals()).thenThrow(new ResourceNotFoundException("Goals", "userId", "x"));

        mockMvc.perform(get("/api/v1/users/me/goals").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/users/me/goals ────────────────────────────────────────────

    @Test
    void updateGoals_shouldReturn200() throws Exception {
        when(userService.updateGoals(any()))
                .thenReturn(UserGoalsDto.builder().dailySteps(8000).build());

        mockMvc.perform(put("/api/v1/users/me/goals")
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"dailySteps":8000}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailySteps").value(8000));
    }

    // ── GET /api/v1/users/me/subscription ────────────────────────────────────

    @Test
    void getSubscription_shouldReturn200() throws Exception {
        when(userService.getSubscription())
                .thenReturn(
                        SubscriptionDto.builder().plan("PRO").status("ACTIVE").build());

        mockMvc.perform(get("/api/v1/users/me/subscription").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"));
    }

    // ── GET /api/v1/users/me/data-export ─────────────────────────────────────

    @Test
    void exportData_shouldReturn200() throws Exception {
        when(userService.exportData())
                .thenReturn(GdprExportDto.builder()
                        .userId(UUID.randomUUID())
                        .email("user@test.com")
                        .exportedAt(java.time.OffsetDateTime.now())
                        .dataJson("{}")
                        .build());

        mockMvc.perform(get("/api/v1/users/me/data-export").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }
}
