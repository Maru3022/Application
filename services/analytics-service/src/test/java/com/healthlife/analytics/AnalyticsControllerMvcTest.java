package com.healthlife.analytics;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.analytics.service.AnalyticsService;
import com.healthlife.common.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
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

/**
 * MockMvc tests for AnalyticsController covering:
 * - POST /analytics/events → 200
 * - GET /analytics/events → 200 with list
 * - GET /analytics/events/count → 200 with count
 * - GET /analytics/summary → 200 with map
 * - 401 when no JWT
 * - 400 when eventName blank
 */
@SpringBootTest(classes = AnalyticsServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    AnalyticsService analyticsService;

    @MockBean
    StringRedisTemplate stringRedisTemplate;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    // ── POST /api/v1/analytics/events ────────────────────────────────────────

    @Test
    void trackEvent_shouldReturn200() throws Exception {
        doNothing().when(analyticsService).trackEvent(any(), anyString(), any());

        mockMvc.perform(post("/api/v1/analytics/events?eventName=login")
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void trackEvent_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/events?eventName=login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void trackEvent_blankEventName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/events?eventName=")
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/analytics/events ─────────────────────────────────────────

    @Test
    void getEvents_shouldReturn200WithList() throws Exception {
        when(analyticsService.getEvents(any(), anyString())).thenReturn(List.of("1000|{}", "2000|{}"));

        mockMvc.perform(get("/api/v1/analytics/events?eventName=login").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getEvents_empty_shouldReturn200WithEmptyList() throws Exception {
        when(analyticsService.getEvents(any(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/events?eventName=nonexistent")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /api/v1/analytics/events/count ───────────────────────────────────

    @Test
    void getEventCount_shouldReturn200WithCount() throws Exception {
        when(analyticsService.getEventCount(any(), anyString())).thenReturn(42L);

        mockMvc.perform(get("/api/v1/analytics/events/count?eventName=login")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(42));
    }

    // ── GET /api/v1/analytics/summary ────────────────────────────────────────

    @Test
    void getUserSummary_shouldReturn200WithMap() throws Exception {
        when(analyticsService.getUserSummary(any())).thenReturn(Map.of("login", 5L, "mood_logged", 12L));

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(5));
    }

    @Test
    void getUserSummary_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().is4xxClientError());
    }
}
