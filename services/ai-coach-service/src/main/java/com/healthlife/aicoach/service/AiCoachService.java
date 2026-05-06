package com.healthlife.aicoach.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.aicoach.entity.AiInsight;
import com.healthlife.aicoach.repository.AiInsightRepository;
import com.healthlife.common.dto.aicoach.*;
import com.healthlife.common.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class AiCoachService {

    private final AiInsightRepository aiInsightRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    // Dedicated thread pool for blocking Claude API calls so Tomcat threads are never blocked.
    private final Executor claudeExecutor;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.claude.base-url:https://api.anthropic.com}")
    private String claudeBaseUrl;

    public AiCoachService(
            AiInsightRepository aiInsightRepository,
            StringRedisTemplate redisTemplate,
            WebClient webClient,
            ObjectMapper objectMapper,
            @Qualifier("claudeExecutor") Executor claudeExecutor) {
        this.aiInsightRepository = aiInsightRepository;
        this.redisTemplate = redisTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.claudeExecutor = claudeExecutor;
    }

    public ChatResponse chat(ChatRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String msgHash = sha256Prefix(request.getMessage());
        String cacheKey = "ai:chat:" + userId + ":" + msgHash;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return ChatResponse.builder()
                    .message(cached)
                    .conversationId(userId.toString())
                    .build();
        }

        String aiResponse = callClaudeApiAsync(request.getMessage(), request.getContext());
        redisTemplate.opsForValue().set(cacheKey, aiResponse, 1, TimeUnit.HOURS);
        return ChatResponse.builder()
                .message(aiResponse)
                .conversationId(userId.toString())
                .build();
    }

    @Transactional
    public InsightDto getDailyInsight() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String cacheKey =
                "ai:insight:daily:" + userId + ":" + LocalDateTime.now().toLocalDate();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return InsightDto.builder()
                    .type("daily")
                    .content(cached)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        String insight = callClaudeApiAsync("Generate a daily health insight based on my recent data", null);
        aiInsightRepository.save(AiInsight.builder()
                .userId(userId)
                .type("daily")
                .content(insight)
                .createdAt(LocalDateTime.now())
                .build());
        redisTemplate.opsForValue().set(cacheKey, insight, 24, TimeUnit.HOURS);
        return InsightDto.builder()
                .type("daily")
                .content(insight)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public InsightDto getWeeklyInsight() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String insight = callClaudeApiAsync("Generate a weekly health report based on my data", null);
        aiInsightRepository.save(AiInsight.builder()
                .userId(userId)
                .type("weekly")
                .content(insight)
                .createdAt(LocalDateTime.now())
                .build());
        return InsightDto.builder()
                .type("weekly")
                .content(insight)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getEnergyPrediction() {
        String prediction = callClaudeApiAsync("Predict my energy level for tomorrow based on my patterns", null);
        return InsightDto.builder()
                .type("energy_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getSymptomPrediction() {
        String prediction = callClaudeApiAsync("Analyze symptom patterns and predict potential issues", null);
        return InsightDto.builder()
                .type("symptom_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getRecommendations() {
        String recs = callClaudeApiAsync("Give me personalized health recommendations for today", null);
        return InsightDto.builder()
                .type("recommendations")
                .content(recs)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public InsightDto analyzeCorrelations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String analysis = callClaudeApiAsync("Analyze correlations between my health metrics", null);
        aiInsightRepository.save(AiInsight.builder()
                .userId(userId)
                .type("correlation")
                .content(analysis)
                .createdAt(LocalDateTime.now())
                .build());
        return InsightDto.builder()
                .type("correlation")
                .content(analysis)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * Executes the Claude API call on the dedicated {@code claudeExecutor} thread pool so that
     * Tomcat request threads are never blocked waiting for an external HTTP response. The calling
     * thread blocks on {@link CompletableFuture#get()} with a hard 35-second timeout.
     */
    private String callClaudeApiAsync(String userMessage, String context) {
        try {
            return CompletableFuture.supplyAsync(() -> callClaudeApi(userMessage, context), claudeExecutor)
                    .get(35, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Claude API call timed out after 35s");
            return "I'm currently unable to process your request due to a timeout. Please try again later.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Request was interrupted. Please try again.";
        } catch (Exception e) {
            log.error("Claude async call failed: {}", e.getMessage());
            return "I'm currently unable to process your request. Please try again later.";
        }
    }

    private String callClaudeApi(String userMessage, String context) {
        if (claudeApiKey == null || claudeApiKey.isEmpty()) {
            log.warn("Claude API key not configured");
            return "AI Coach is not configured. Please set the Claude API key.";
        }
        try {
            String systemPrompt = "You are HealthLife AI Coach, a health and wellness assistant. "
                    + "Provide evidence-based, supportive health insights. "
                    + "Never provide medical diagnoses. Always recommend consulting healthcare"
                    + " professionals.";
            String fullUserMessage = (context != null ? context + "\n" : "") + userMessage;
            Map<String, Object> requestMap = Map.of(
                    "model",
                    "claude-3-5-sonnet-20241022",
                    "max_tokens",
                    1024,
                    "system",
                    systemPrompt,
                    "messages",
                    List.of(Map.of("role", "user", "content", fullUserMessage)));
            String requestBody = objectMapper.writeValueAsString(requestMap);
            return webClient
                    .post()
                    .uri(claudeBaseUrl + "/v1/messages")
                    .header("x-api-key", claudeApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            return "I'm currently unable to process your request. Please try again later.";
        }
    }

    private String sha256Prefix(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }
}
