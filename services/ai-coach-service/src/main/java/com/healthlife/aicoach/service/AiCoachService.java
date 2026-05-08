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

/**
 * AI Coach service backed by DeepSeek V4 Flash (deepseek-v4-flash model).
 *
 * <p>DeepSeek provides an OpenAI-compatible API, so the request format is identical to
 * OpenAI's chat completions endpoint. The base URL is https://api.deepseek.com and the
 * model is {@code deepseek-v4-flash} (284B total / 13B active params, 1M context).
 *
 * <p>All API calls are executed on a dedicated thread pool ({@code deepseekExecutor}) so
 * Tomcat request threads are never blocked waiting for an external HTTP response.
 */
@Slf4j
@Service
public class AiCoachService {

    private final AiInsightRepository aiInsightRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    // Dedicated thread pool for blocking DeepSeek API calls so Tomcat threads are never blocked.
    private final Executor deepseekExecutor;

    @Value("${ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model:deepseek-v4-flash}")
    private String deepseekModel;

    public AiCoachService(
            AiInsightRepository aiInsightRepository,
            StringRedisTemplate redisTemplate,
            WebClient webClient,
            ObjectMapper objectMapper,
            @Qualifier("deepseekExecutor") Executor deepseekExecutor) {
        this.aiInsightRepository = aiInsightRepository;
        this.redisTemplate = redisTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.deepseekExecutor = deepseekExecutor;
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

        String aiResponse = callDeepSeekAsync(request.getMessage(), request.getContext());
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

        String insight = callDeepSeekAsync("Generate a daily health insight based on my recent data", null);
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
        String insight = callDeepSeekAsync("Generate a weekly health report based on my data", null);
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
        String prediction = callDeepSeekAsync("Predict my energy level for tomorrow based on my patterns", null);
        return InsightDto.builder()
                .type("energy_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getSymptomPrediction() {
        String prediction = callDeepSeekAsync("Analyze symptom patterns and predict potential issues", null);
        return InsightDto.builder()
                .type("symptom_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getRecommendations() {
        String recs = callDeepSeekAsync("Give me personalized health recommendations for today", null);
        return InsightDto.builder()
                .type("recommendations")
                .content(recs)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public InsightDto analyzeCorrelations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String analysis = callDeepSeekAsync("Analyze correlations between my health metrics", null);
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
     * Executes the DeepSeek API call on the dedicated {@code deepseekExecutor} thread pool so
     * that Tomcat request threads are never blocked waiting for an external HTTP response.
     */
    private String callDeepSeekAsync(String userMessage, String context) {
        try {
            return CompletableFuture.supplyAsync(() -> callDeepSeekApi(userMessage, context), deepseekExecutor)
                    .get(35, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("DeepSeek API call timed out after 35s");
            return "I'm currently unable to process your request due to a timeout. Please try again later.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Request was interrupted. Please try again.";
        } catch (Exception e) {
            log.error("DeepSeek async call failed: {}", e.getMessage());
            return "I'm currently unable to process your request. Please try again later.";
        }
    }

    /**
     * Calls the DeepSeek API using the OpenAI-compatible chat completions endpoint.
     *
     * <p>DeepSeek V4 Flash (deepseek-v4-flash) supports the same request/response format as OpenAI,
     * so we use the standard {@code /v1/chat/completions} path with a Bearer token.
     */
    private String callDeepSeekApi(String userMessage, String context) {
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            log.warn("DeepSeek API key not configured");
            return "AI Coach is not configured. Please set the DEEPSEEK_API_KEY environment variable.";
        }
        try {
            String systemPrompt = "You are HealthLife AI Coach, a health and wellness assistant. "
                    + "Provide evidence-based, supportive health insights. "
                    + "Never provide medical diagnoses. Always recommend consulting healthcare"
                    + " professionals.";
            String fullUserMessage = (context != null ? context + "\n" : "") + userMessage;

            // OpenAI-compatible request format (DeepSeek uses the same schema)
            Map<String, Object> requestMap = Map.of(
                    "model",
                    deepseekModel,
                    "max_tokens",
                    1024,
                    "messages",
                    List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", fullUserMessage)));

            String requestBody = objectMapper.writeValueAsString(requestMap);

            String rawResponse = webClient
                    .post()
                    .uri(deepseekBaseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + deepseekApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            // Extract content from OpenAI-compatible response:
            // {"choices":[{"message":{"content":"..."}}]}
            return extractContent(rawResponse);
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return "I'm currently unable to process your request. Please try again later.";
        }
    }

    /**
     * Extracts the assistant message content from an OpenAI-compatible chat completion response.
     */
    @SuppressWarnings("unchecked")
    private String extractContent(String rawResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(rawResponse, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return rawResponse;
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return rawResponse;
            Object content = message.get("content");
            return content != null ? content.toString() : rawResponse;
        } catch (Exception e) {
            log.warn("Failed to parse DeepSeek response, returning raw: {}", e.getMessage());
            return rawResponse;
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
