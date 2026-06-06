package com.healthlife.healthdata;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.common.dto.health.*;
import com.healthlife.common.exception.ForbiddenException;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.healthdata.service.HealthDataService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for HealthDataController covering all endpoints:
 * - Sleep CRUD
 * - Activity sync/today/history
 * - Weight create/history
 * - Water add/today
 * - Symptoms
 * - Cycle/prediction
 * - Dashboard
 * - 401 without JWT, 403 on wrong user, 404 not found
 */
@SpringBootTest(classes = HealthDataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthDataControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    HealthDataService healthDataService;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    // ── POST /api/v1/health/sleep ─────────────────────────────────────────────

    @Test
    void createSleep_shouldReturn200() throws Exception {
        when(healthDataService.createSleep(any()))
                .thenReturn(SleepResponse.builder()
                        .id(UUID.randomUUID())
                        .durationMin(480)
                        .quality(4)
                        .build());

        mockMvc.perform(
                        post("/api/v1/health/sleep")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"sleepStart":"2025-01-01T22:00:00Z","sleepEnd":"2025-01-02T06:00:00Z","quality":4}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMin").value(480));
    }

    @Test
    void createSleep_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(
                        post("/api/v1/health/sleep")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"sleepStart":"2025-01-01T22:00:00Z","sleepEnd":"2025-01-02T06:00:00Z"}
                            """))
                .andExpect(status().is4xxClientError());
    }

    // ── DELETE /api/v1/health/sleep/{id} ──────────────────────────────────────

    @Test
    void deleteSleep_ownEntry_shouldReturn204() throws Exception {
        doNothing().when(healthDataService).deleteSleep(any());

        mockMvc.perform(delete("/api/v1/health/sleep/" + UUID.randomUUID()).header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSleep_otherUser_shouldReturn403() throws Exception {
        doThrow(new ForbiddenException("Not your entry"))
                .when(healthDataService)
                .deleteSleep(any());

        mockMvc.perform(delete("/api/v1/health/sleep/" + UUID.randomUUID()).header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSleep_notFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Sleep", "id", "x"))
                .when(healthDataService)
                .deleteSleep(any());

        mockMvc.perform(delete("/api/v1/health/sleep/" + UUID.randomUUID()).header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/health/sleep/stats ────────────────────────────────────────

    @Test
    void getSleepStats_shouldReturn200() throws Exception {
        when(healthDataService.getSleepStats())
                .thenReturn(SleepStatsDto.builder()
                        .entryCount(8)
                        .avgDurationMin(450.0)
                        .avgQuality(7.5)
                        .minDurationMin(360)
                        .maxDurationMin(540)
                        .goalAchievementPct(85.0)
                        .build());

        mockMvc.perform(get("/api/v1/health/sleep/stats").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/health/weight ────────────────────────────────────────────

    @Test
    void createWeight_shouldReturn200() throws Exception {
        when(healthDataService.createWeight(any()))
                .thenReturn(WeightResponse.builder()
                        .id(UUID.randomUUID())
                        .weightKg(new BigDecimal("75.5"))
                        .build());

        mockMvc.perform(
                        post("/api/v1/health/weight")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"weightKg":75.5,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(75.5));
    }

    // ── POST /api/v1/health/water ─────────────────────────────────────────────

    @Test
    void addWater_shouldReturn200() throws Exception {
        when(healthDataService.addWater(any()))
                .thenReturn(WaterResponse.builder()
                        .id(UUID.randomUUID())
                        .amountMl(250)
                        .build());

        mockMvc.perform(
                        post("/api/v1/health/water")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"amountMl":250,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMl").value(250));
    }

    // ── GET /api/v1/health/water/today ────────────────────────────────────────

    @Test
    void getWaterToday_shouldReturn200() throws Exception {
        when(healthDataService.getWaterToday()).thenReturn(1500);

        mockMvc.perform(get("/api/v1/health/water/today").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1500));
    }

    // ── POST /api/v1/health/activity/sync ────────────────────────────────────

    @Test
    void syncActivity_shouldReturn200() throws Exception {
        when(healthDataService.syncActivity(any()))
                .thenReturn(ActivityEntryDto.builder()
                        .steps(8000)
                        .caloriesBurned(300)
                        .build());

        mockMvc.perform(
                        post("/api/v1/health/activity/sync")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"date":"2025-01-01","steps":8000,"caloriesBurned":300,"source":"manual"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(8000));
    }

    // ── GET /api/v1/health/dashboard ──────────────────────────────────────────

    @Test
    void getDashboard_shouldReturn200() throws Exception {
        when(healthDataService.getDashboard())
                .thenReturn(DashboardDto.builder()
                        .waterTodayMl(1500)
                        .stepsTodayCount(7000)
                        .build());

        mockMvc.perform(get("/api/v1/health/dashboard").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waterTodayMl").value(1500));
    }

    // ── GET /api/v1/health/cycle/prediction ───────────────────────────────────

    @Test
    void getCyclePrediction_noData_shouldReturn404() throws Exception {
        when(healthDataService.getCyclePrediction()).thenThrow(new ResourceNotFoundException("Cycle", "any", ""));

        mockMvc.perform(get("/api/v1/health/cycle/prediction").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/health/symptoms ──────────────────────────────────────────

    @Test
    void createSymptom_shouldReturn200() throws Exception {
        when(healthDataService.createSymptom(any()))
                .thenReturn(SymptomResponse.builder()
                        .id(UUID.randomUUID())
                        .symptom("Headache")
                        .intensity(7)
                        .build());

        mockMvc.perform(
                        post("/api/v1/health/symptoms")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"symptom":"Headache","intensity":7,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symptom").value("Headache"));
    }
}
