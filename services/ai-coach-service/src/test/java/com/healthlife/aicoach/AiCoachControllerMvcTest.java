package com.healthlife.aicoach;

import com.healthlife.aicoach.service.AiCoachService;
import com.healthlife.common.dto.aicoach.*;
import com.healthlife.common.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for AiCoachController covering all endpoints:
 * - POST /ai/chat → 200
 * - GET /ai/insights/daily → 200
 * - GET /ai/insights/weekly → 200
 * - GET /ai/predictions/energy → 200
 * - GET /ai/predictions/symptoms → 200
 * - GET /ai/recommendations → 200
 * - POST /ai/analyze/correlations → 200
 * - 401 without JWT
 */
@SpringBootTest(classes = AiCoachServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCoachControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    AiCoachService aiCoachService;

    @MockBean
    StringRedisTemplate redisTemplate;

    @MockBean
    WebClient webClient;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    private InsightDto insight(String type) {
        return InsightDto.builder()
                .type(type)
                .content("Test insight content for " + type)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/ai/chat ──────────────────────────────────────────────────

    @Test
    void chat_shouldReturn200() throws Exception {
        when(aiCoachService.chat(any()))
                .thenReturn(ChatResponse.builder()
                        .message("Here is your answer")
                        .conversationId("conv-1")
                        .build());

        mockMvc.perform(
                        post("/api/v1/ai/chat")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"message":"How can I improve my sleep?"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Here is your answer"));
    }

    @Test
    void chat_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"message":"Hello"}
                            """))
                .andExpect(status().is4xxClientError());
    }

    // ── GET /api/v1/ai/insights/daily ────────────────────────────────────────

    @Test
    void getDailyInsight_shouldReturn200() throws Exception {
        when(aiCoachService.getDailyInsight()).thenReturn(insight("daily"));

        mockMvc.perform(get("/api/v1/ai/insights/daily").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("daily"));
    }

    @Test
    void getDailyInsight_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(get("/api/v1/ai/insights/daily")).andExpect(status().is4xxClientError());
    }

    // ── GET /api/v1/ai/insights/weekly ───────────────────────────────────────

    @Test
    void getWeeklyInsight_shouldReturn200() throws Exception {
        when(aiCoachService.getWeeklyInsight()).thenReturn(insight("weekly"));

        mockMvc.perform(get("/api/v1/ai/insights/weekly").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("weekly"));
    }

    // ── GET /api/v1/ai/predictions/energy ────────────────────────────────────

    @Test
    void getEnergyPrediction_shouldReturn200() throws Exception {
        when(aiCoachService.getEnergyPrediction()).thenReturn(insight("energy_prediction"));

        mockMvc.perform(get("/api/v1/ai/predictions/energy").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("energy_prediction"));
    }

    // ── GET /api/v1/ai/predictions/symptoms ──────────────────────────────────

    @Test
    void getSymptomPrediction_shouldReturn200() throws Exception {
        when(aiCoachService.getSymptomPrediction()).thenReturn(insight("symptom_prediction"));

        mockMvc.perform(get("/api/v1/ai/predictions/symptoms").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("symptom_prediction"));
    }

    // ── GET /api/v1/ai/recommendations ───────────────────────────────────────

    @Test
    void getRecommendations_shouldReturn200() throws Exception {
        when(aiCoachService.getRecommendations()).thenReturn(insight("recommendations"));

        mockMvc.perform(get("/api/v1/ai/recommendations").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("recommendations"));
    }

    // ── POST /api/v1/ai/analyze/correlations ─────────────────────────────────

    @Test
    void analyzeCorrelations_shouldReturn200() throws Exception {
        when(aiCoachService.analyzeCorrelations()).thenReturn(insight("correlation"));

        mockMvc.perform(post("/api/v1/ai/analyze/correlations").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("correlation"));
    }

    @Test
    void analyzeCorrelations_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/ai/analyze/correlations")).andExpect(status().is4xxClientError());
    }

    // ── insight content ───────────────────────────────────────────────────────

    @Test
    void getAllInsightEndpoints_returnNonBlankContent() throws Exception {
        when(aiCoachService.getDailyInsight()).thenReturn(insight("daily"));
        when(aiCoachService.getWeeklyInsight()).thenReturn(insight("weekly"));
        when(aiCoachService.getEnergyPrediction()).thenReturn(insight("energy_prediction"));
        when(aiCoachService.getSymptomPrediction()).thenReturn(insight("symptom_prediction"));
        when(aiCoachService.getRecommendations()).thenReturn(insight("recommendations"));

        String token = "Bearer " + jwt();

        mockMvc.perform(get("/api/v1/ai/insights/daily").header("Authorization", token))
                .andExpect(jsonPath("$.content").isNotEmpty());
        mockMvc.perform(get("/api/v1/ai/insights/weekly").header("Authorization", token))
                .andExpect(jsonPath("$.content").isNotEmpty());
        mockMvc.perform(get("/api/v1/ai/predictions/energy").header("Authorization", token))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }
}
