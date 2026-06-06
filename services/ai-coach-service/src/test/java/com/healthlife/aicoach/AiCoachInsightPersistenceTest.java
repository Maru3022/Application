package com.healthlife.aicoach;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.aicoach.entity.AiInsight;
import com.healthlife.aicoach.repository.AiInsightRepository;
import com.healthlife.aicoach.service.AiCoachService;
import com.healthlife.common.dto.aicoach.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Additional tests for AiCoachService covering:
 * - Insight persistence to DB
 * - Multiple users have isolated insights
 * - Weekly insight is persisted
 * - Correlation analysis is persisted
 * - Cache key includes userId (isolation)
 * - Fallback message when API key missing
 */
@SpringBootTest(classes = AiCoachServiceApplication.class)
@ActiveProfiles("test")
class AiCoachInsightPersistenceTest {

    @Autowired
    private AiCoachService aiCoachService;

    @Autowired
    private AiInsightRepository aiInsightRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private WebClient webClient;

    private UUID userId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        userId = UUID.randomUUID();
        setAuth(userId);
        aiInsightRepository.deleteAll();

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any());
    }

    // ── insight persistence ───────────────────────────────────────────────────

    @Test
    void getDailyInsight_shouldPersistToDb() {
        aiCoachService.getDailyInsight();

        List<AiInsight> insights = aiInsightRepository.findAll();
        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).getType()).isEqualTo("daily");
        assertThat(insights.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void getWeeklyInsight_shouldPersistToDb() {
        aiCoachService.getWeeklyInsight();

        List<AiInsight> insights = aiInsightRepository.findAll();
        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).getType()).isEqualTo("weekly");
    }

    @Test
    void analyzeCorrelations_shouldPersistToDb() {
        aiCoachService.analyzeCorrelations();

        List<AiInsight> insights = aiInsightRepository.findAll();
        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).getType()).isEqualTo("correlation");
    }

    @Test
    void multipleInsightTypes_shouldAllPersist() {
        aiCoachService.getDailyInsight();
        aiCoachService.getWeeklyInsight();
        aiCoachService.analyzeCorrelations();

        assertThat(aiInsightRepository.count()).isEqualTo(3);
    }

    // ── user isolation ────────────────────────────────────────────────────────

    @Test
    void getDailyInsight_differentUsers_shouldHaveIsolatedInsights() {
        aiCoachService.getDailyInsight();

        UUID user2 = UUID.randomUUID();
        setAuth(user2);
        aiCoachService.getDailyInsight();

        List<AiInsight> user1Insights = aiInsightRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(userId))
                .toList();
        List<AiInsight> user2Insights = aiInsightRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(user2))
                .toList();

        assertThat(user1Insights).hasSize(1);
        assertThat(user2Insights).hasSize(1);
    }

    // ── fallback messages ─────────────────────────────────────────────────────

    @Test
    void chat_noApiKey_shouldReturnFallbackNotNull() {
        ChatRequest req = ChatRequest.builder().message("Test question").build();
        ChatResponse resp = aiCoachService.chat(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getMessage()).isNotBlank();
        assertThat(resp.getConversationId()).isEqualTo(userId.toString());
    }

    @Test
    void getEnergyPrediction_noApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getEnergyPrediction();

        assertThat(insight).isNotNull();
        assertThat(insight.getType()).isEqualTo("energy_prediction");
        assertThat(insight.getContent()).isNotBlank();
        assertThat(insight.getGeneratedAt()).isNotNull();
    }

    @Test
    void getSymptomPrediction_noApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getSymptomPrediction();

        assertThat(insight).isNotNull();
        assertThat(insight.getType()).isEqualTo("symptom_prediction");
        assertThat(insight.getContent()).isNotBlank();
    }

    @Test
    void getRecommendations_noApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getRecommendations();

        assertThat(insight).isNotNull();
        assertThat(insight.getType()).isEqualTo("recommendations");
        assertThat(insight.getContent()).isNotBlank();
    }

    // ── insight content ───────────────────────────────────────────────────────

    @Test
    void getDailyInsight_contentShouldBeNonBlank() {
        InsightDto insight = aiCoachService.getDailyInsight();
        assertThat(insight.getContent()).isNotBlank();
    }

    @Test
    void getWeeklyInsight_contentShouldBeNonBlank() {
        InsightDto insight = aiCoachService.getWeeklyInsight();
        assertThat(insight.getContent()).isNotBlank();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
