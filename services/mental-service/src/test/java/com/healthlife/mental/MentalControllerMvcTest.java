package com.healthlife.mental;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.mental.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.mental.service.MentalService;
import com.healthlife.mental.support.client.AnthropicClient;
import com.healthlife.mental.support.service.SupportChatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for MentalController and SupportController covering:
 * - POST /mental/mood → 200
 * - GET /mental/mood/history → 200 with list
 * - POST /mental/journal → 200
 * - POST /mental/stress → 200
 * - GET /mental/meditations → 200
 * - GET /mental/meditations/recommended → 404 when none
 * - POST /support/chat → 200
 * - 401 when no JWT
 */
@SpringBootTest(classes = MentalServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentalControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    MentalService mentalService;

    @MockBean
    SupportChatService supportChatService;

    @MockBean
    AnthropicClient anthropicClient;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    // ── POST /api/v1/mental/mood ──────────────────────────────────────────────

    @Test
    void createMood_shouldReturn200() throws Exception {
        when(mentalService.createMood(any()))
                .thenReturn(MoodResponse.builder()
                        .id(UUID.randomUUID())
                        .moodScore(8)
                        .recordedAt(OffsetDateTime.now())
                        .build());

        mockMvc.perform(
                        post("/api/v1/mental/mood")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"moodScore":8,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodScore").value(8));
    }

    @Test
    void createMood_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(
                        post("/api/v1/mental/mood")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"moodScore":8,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().is4xxClientError());
    }

    // ── GET /api/v1/mental/mood/history ──────────────────────────────────────

    @Test
    void getMoodHistory_shouldReturn200WithList() throws Exception {
        when(mentalService.getMoodHistory())
                .thenReturn(List.of(
                        MoodResponse.builder()
                                .id(UUID.randomUUID())
                                .moodScore(7)
                                .build(),
                        MoodResponse.builder()
                                .id(UUID.randomUUID())
                                .moodScore(9)
                                .build()));

        mockMvc.perform(get("/api/v1/mental/mood/history").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getMoodHistory_empty_shouldReturn200WithEmptyList() throws Exception {
        when(mentalService.getMoodHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mental/mood/history").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── POST /api/v1/mental/journal ───────────────────────────────────────────

    @Test
    void createJournal_shouldReturn200() throws Exception {
        when(mentalService.createJournal(any()))
                .thenReturn(JournalResponse.builder()
                        .id(UUID.randomUUID())
                        .content("My thoughts")
                        .build());

        mockMvc.perform(
                        post("/api/v1/mental/journal")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"content":"My thoughts","recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("My thoughts"));
    }

    // ── POST /api/v1/mental/stress ────────────────────────────────────────────

    @Test
    void createStress_shouldReturn200() throws Exception {
        when(mentalService.createStress(any()))
                .thenReturn(
                        StressResponse.builder().id(UUID.randomUUID()).level(6).build());

        mockMvc.perform(
                        post("/api/v1/mental/stress")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"level":6,"recordedAt":"2025-01-01T10:00:00Z"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(6));
    }

    // ── GET /api/v1/mental/meditations ────────────────────────────────────────

    @Test
    void getMeditations_shouldReturn200() throws Exception {
        when(mentalService.getMeditations(any()))
                .thenReturn(List.of(MeditationDto.builder()
                        .id(UUID.randomUUID())
                        .title("Calm")
                        .category("sleep")
                        .build()));

        mockMvc.perform(get("/api/v1/mental/meditations").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Calm"));
    }

    @Test
    void getMeditations_withCategory_shouldReturn200() throws Exception {
        when(mentalService.getMeditations("sleep"))
                .thenReturn(List.of(MeditationDto.builder()
                        .id(UUID.randomUUID())
                        .title("Sleep Well")
                        .category("sleep")
                        .build()));

        mockMvc.perform(get("/api/v1/mental/meditations?category=sleep").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("sleep"));
    }

    // ── GET /api/v1/mental/meditations/recommended ────────────────────────────

    @Test
    void getRecommendedMeditation_notFound_shouldReturn404() throws Exception {
        when(mentalService.getRecommendedMeditation())
                .thenThrow(new ResourceNotFoundException("Meditation", "any", ""));

        mockMvc.perform(get("/api/v1/mental/meditations/recommended").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/support/chat ─────────────────────────────────────────────

    @Test
    void supportChat_shouldReturn200() throws Exception {
        when(supportChatService.chat(any(), any()))
                .thenReturn(
                        new com.healthlife.mental.support.dto.SupportChatResponse("Привет! Чем могу помочь?", "ru"));

        mockMvc.perform(
                        post("/api/v1/support/chat")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"message":"Привет","language":"ru"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ru"));
    }

    @Test
    void supportChat_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(
                        post("/api/v1/support/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"message":"Hello","language":"en"}
                            """))
                .andExpect(status().is4xxClientError());
    }
}
