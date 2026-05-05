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
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCoachService {

    private final AiInsightRepository aiInsightRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.claude.base-url:https://api.anthropic.com}")
    private String claudeBaseUrl;

    public ChatResponse chat(ChatRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: hashCode() is not a safe cache key — use SHA-256 digest instead to avoid collisions
        String msgHash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(request.getMessage().getBytes(StandardCharsets.UTF_8));
            msgHash = HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            msgHash = String.valueOf(Math.abs(request.getMessage().hashCode()));
        }
        String cacheKey = "ai:chat:" + userId + ":" + msgHash;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return ChatResponse.builder()
                    .message(cached)
                    .conversationId(userId.toString())
                    .build();
        }

        String aiResponse = callClaudeApi(request.getMessage(), request.getContext());
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

        String insight = callClaudeApi("Generate a daily health insight based on my recent data", null);

        AiInsight entity = AiInsight.builder()
                .userId(userId)
                .type("daily")
                .content(insight)
                .createdAt(LocalDateTime.now())
                .build();
        aiInsightRepository.save(entity);
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
        String insight = callClaudeApi("Generate a weekly health report based on my data", null);

        AiInsight entity = AiInsight.builder()
                .userId(userId)
                .type("weekly")
                .content(insight)
                .createdAt(LocalDateTime.now())
                .build();
        aiInsightRepository.save(entity);

        return InsightDto.builder()
                .type("weekly")
                .content(insight)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getEnergyPrediction() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String prediction = callClaudeApi("Predict my energy level for tomorrow based on my patterns", null);
        return InsightDto.builder()
                .type("energy_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getSymptomPrediction() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String prediction = callClaudeApi("Analyze symptom patterns and predict potential issues", null);
        return InsightDto.builder()
                .type("symptom_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getRecommendations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String recs = callClaudeApi("Give me personalized health recommendations for today", null);
        return InsightDto.builder()
                .type("recommendations")
                .content(recs)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public InsightDto analyzeCorrelations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String analysis = callClaudeApi("Analyze correlations between my health metrics", null);

        AiInsight entity = AiInsight.builder()
                .userId(userId)
                .type("correlation")
                .content(analysis)
                .createdAt(LocalDateTime.now())
                .build();
        aiInsightRepository.save(entity);

        return InsightDto.builder()
                .type("correlation")
                .content(analysis)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String callClaudeApi(String userMessage, String context) {
        if (claudeApiKey == null || claudeApiKey.isEmpty()) {
            log.warn("Claude API key not configured");
            return "AI Coach is not configured. Please set the Claude API key.";
        }

        try {
            String systemPrompt = "You are HealthLife AI Coach, a health and wellness assistant. "
                    + "Provide evidence-based, supportive health insights. "
                    + "Never provide medical diagnoses. Always recommend consulting healthcare professionals.";

            // FIX: build JSON safely with Jackson instead of string formatting (prevents injection)
            ObjectMapper mapper = new ObjectMapper();
            String fullUserMessage = (context != null ? context + "\n" : "") + userMessage;

            Map<String, Object> requestMap = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "max_tokens", 1024,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", fullUserMessage)));

            String requestBody = mapper.writeValueAsString(requestMap);

            // FIX: .block() is acceptable here since this service runs on a Spring MVC (not WebFlux) thread
            // but we add a timeout to prevent indefinite blocking
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
}
