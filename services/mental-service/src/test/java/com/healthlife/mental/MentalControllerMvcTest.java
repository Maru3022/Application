package com.healthlife.mental;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.mental.*;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.mental.service.MentalService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentalControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    MentalService mentalService;

    private UUID userId = UUID.randomUUID();
    private String jwtToken;

    @BeforeEach
    void setup() {
        jwtToken = jwtTokenProvider.generateAccessToken(userId, "test@health.com", "USER");
    }

    @Test
    void createMood_shouldReturn200() throws Exception {
        MoodResponse mockResponse = MoodResponse.builder().id(UUID.randomUUID()).build();
        when(mentalService.createMood(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/mental/mood")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"moodScore":8,"note":"Great day","recordedAt":"%s"}
                                """
                                        .formatted(java.time.OffsetDateTime.now())))
                .andExpect(status().isOk());
    }

    @Test
    void getMoodHistory_shouldReturn200() throws Exception {
        when(mentalService.getMoodHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mental/mood/history").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMoodPatterns_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/mental/mood/patterns").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void createJournal_shouldReturn200() throws Exception {
        JournalResponse mockResponse =
                JournalResponse.builder().id(UUID.randomUUID()).build();
        when(mentalService.createJournal(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/mental/journal")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"content":"Today was amazing","recordedAt":"%s"}
                                """
                                        .formatted(java.time.OffsetDateTime.now())))
                .andExpect(status().isOk());
    }

    @Test
    void getJournals_shouldReturn200() throws Exception {
        when(mentalService.getJournals()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mental/journal").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getJournalThemes_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/mental/journal/themes").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void createStress_shouldReturn200() throws Exception {
        StressResponse mockResponse =
                StressResponse.builder().id(UUID.randomUUID()).build();
        when(mentalService.createStress(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/mental/stress")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"level":3,"recordedAt":"%s"}
                                """
                                        .formatted(java.time.OffsetDateTime.now())))
                .andExpect(status().isOk());
    }

    @Test
    void getStressStats_shouldReturn200() throws Exception {
        when(mentalService.getStressStats()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mental/stress/stats").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMeditations_shouldReturn200() throws Exception {
        when(mentalService.getMeditations(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mental/meditations")
                        .param("category", "relaxation")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void completeMeditation_shouldReturn200() throws Exception {
        doNothing().when(mentalService).completeMeditation(any());

        mockMvc.perform(post("/api/v1/mental/meditations/" + UUID.randomUUID() + "/complete")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getRecommendedMeditation_shouldReturn200() throws Exception {
        MeditationDto mockMeditation = MeditationDto.builder()
                .id(UUID.randomUUID())
                .title("Calm Meditation")
                .build();
        when(mentalService.getRecommendedMeditation()).thenReturn(mockMeditation);

        mockMvc.perform(get("/api/v1/mental/meditations/recommended").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void createBreathingSession_shouldReturn200() throws Exception {
        doNothing().when(mentalService).createBreathingSession(any());

        mockMvc.perform(
                        post("/api/v1/mental/breathing/session")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"durationMin":5,"technique":"4-7-8"}
                                """))
                .andExpect(status().isOk());
    }
}
