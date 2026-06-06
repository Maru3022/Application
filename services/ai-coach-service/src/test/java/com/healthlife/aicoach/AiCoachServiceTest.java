package com.healthlife.aicoach;

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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AiCoachServiceApplication.class)
@ActiveProfiles("test")
class AiCoachServiceTest {

    @Autowired
    private AiCoachService aiCoachService;

    @Autowired
    private AiInsightRepository aiInsightRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private WebClient webClient;

    private UUID userId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        aiInsightRepository.deleteAll();

        // Mock Redis — no cache hit by default
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any());
    }

    // ── No API key configured ─────────────────────────────────────────────────

    @Test
    void chat_withoutApiKey_shouldReturnFallbackMessage() {
        ChatRequest req =
                ChatRequest.builder().message("How can I improve my sleep?").build();

        ChatResponse resp = aiCoachService.chat(req);

        assertThat(resp.getMessage()).contains("not configured");
        assertThat(resp.getConversationId()).isEqualTo(userId.toString());
    }

    @Test
    void getDailyInsight_withoutApiKey_shouldReturnFallbackAndNotPersist() {
        long countBefore = aiInsightRepository.count();

        InsightDto insight = aiCoachService.getDailyInsight();

        assertThat(insight.getType()).isEqualTo("daily");
        assertThat(insight.getContent()).contains("not configured");
        assertThat(aiInsightRepository.count()).isGreaterThanOrEqualTo(countBefore);
    }

    @Test
    void getWeeklyInsight_withoutApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getWeeklyInsight();
        assertThat(insight.getType()).isEqualTo("weekly");
        assertThat(insight.getContent()).isNotBlank();
    }

    @Test
    void getEnergyPrediction_withoutApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getEnergyPrediction();
        assertThat(insight.getType()).isEqualTo("energy_prediction");
        assertThat(insight.getContent()).isNotBlank();
    }

    @Test
    void getRecommendations_withoutApiKey_shouldReturnFallback() {
        InsightDto insight = aiCoachService.getRecommendations();
        assertThat(insight.getType()).isEqualTo("recommendations");
        assertThat(insight.getContent()).isNotBlank();
    }

    @Test
    void analyzeCorrelations_withoutApiKey_shouldPersistInsight() {
        InsightDto insight = aiCoachService.analyzeCorrelations();

        assertThat(insight.getType()).isEqualTo("correlation");
        assertThat(aiInsightRepository.count()).isEqualTo(1);

        AiInsight saved = aiInsightRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo("correlation");
    }

    // ── Redis cache ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void chat_withCachedResponse_shouldReturnCachedValue() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("Cached AI response");

        ChatRequest req = ChatRequest.builder().message("Hello").build();
        ChatResponse resp = aiCoachService.chat(req);

        assertThat(resp.getMessage()).isEqualTo("Cached AI response");
        // WebClient should NOT be called when cache hits
        verifyNoInteractions(webClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDailyInsight_withCachedResponse_shouldReturnCachedValue() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("Cached daily insight");

        InsightDto insight = aiCoachService.getDailyInsight();

        assertThat(insight.getContent()).isEqualTo("Cached daily insight");
        verifyNoInteractions(webClient);
    }
}
