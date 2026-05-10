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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Coach service backed by DeepSeek Chat / V3 compatible models.
 *
 * <p>Before every AI call, fetches the user's health context (dashboard + profile) from
 * internal services and injects it into the system prompt so the AI responds with
 * personalised, data-driven insights instead of generic advice.
 *
 * <p>Retry logic: exponential backoff on 429 (rate limit) and 503 (service unavailable).
 */
@Slf4j
@Service
public class AiCoachService {

    private final AiInsightRepository aiInsightRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Executor deepseekExecutor;

    @Value("${internal.health-data-service.url:http://health-data-service:8083}")
    private String healthDataServiceUrl;

    @Value("${internal.user-service.url:http://user-service:8082}")
    private String userServiceUrl;

    @Value("${ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

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

        // Fetch user context and inject into the message
        String userContext = buildUserContext(userId, request.getContext());
        String aiResponse = callDeepSeekAsync(request.getMessage(), userContext);
        redisTemplate.opsForValue().set(cacheKey, aiResponse, 1, TimeUnit.HOURS);
        return ChatResponse.builder()
                .message(aiResponse)
                .conversationId(userId.toString())
                .build();
    }

    /**
     * Streaming chat via Server-Sent Events. Calls DeepSeek with stream=true and forwards
     * each token chunk to the SSE emitter as it arrives.
     */
    public SseEmitter chatStream(ChatRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(60_000L);

        CompletableFuture.runAsync(
                () -> {
                    try {
                        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("AI Coach is not configured. Set DEEPSEEK_API_KEY."));
                            emitter.complete();
                            return;
                        }

                        String userContext = buildUserContext(userId, request.getContext());
                        String systemPrompt = buildSystemPrompt(userContext);
                        String fullUserMessage = request.getMessage();

                        Map<String, Object> requestMap = Map.of(
                                "model",
                                deepseekModel,
                                "max_tokens",
                                1024,
                                "stream",
                                true,
                                "messages",
                                List.of(
                                        Map.of("role", "system", "content", systemPrompt),
                                        Map.of("role", "user", "content", fullUserMessage)));

                        String requestBody = objectMapper.writeValueAsString(requestMap);
                        StringBuilder fullResponse = new StringBuilder();

                        webClient
                                .post()
                                .uri(deepseekBaseUrl + "/v1/chat/completions")
                                .header("Authorization", "Bearer " + deepseekApiKey)
                                .header("Content-Type", "application/json")
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToFlux(String.class)
                                .timeout(Duration.ofSeconds(55))
                                .doOnNext(chunk -> {
                                    try {
                                        if (chunk.startsWith("data: ")) {
                                            String data = chunk.substring(6).trim();
                                            if ("[DONE]".equals(data)) {
                                                emitter.complete();
                                                return;
                                            }
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> parsed = objectMapper.readValue(data, Map.class);
                                            @SuppressWarnings("unchecked")
                                            List<Map<String, Object>> choices =
                                                    (List<Map<String, Object>>) parsed.get("choices");
                                            if (choices != null && !choices.isEmpty()) {
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> delta = (Map<String, Object>)
                                                        choices.get(0).get("delta");
                                                if (delta != null && delta.get("content") != null) {
                                                    String token =
                                                            delta.get("content").toString();
                                                    fullResponse.append(token);
                                                    emitter.send(SseEmitter.event()
                                                            .name("token")
                                                            .data(token));
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("SSE chunk parse error: {}", e.getMessage());
                                    }
                                })
                                .doOnError(e -> {
                                    log.error("SSE stream error: {}", e.getMessage());
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("error")
                                                .data("Stream error. Please try again."));
                                    } catch (Exception ignored) {
                                    }
                                    emitter.completeWithError(e);
                                })
                                .doOnComplete(() -> {
                                    if (!fullResponse.isEmpty()) {
                                        String msgHash = sha256Prefix(request.getMessage());
                                        String cacheKey = "ai:chat:" + userId + ":" + msgHash;
                                        redisTemplate
                                                .opsForValue()
                                                .set(cacheKey, fullResponse.toString(), 1, TimeUnit.HOURS);
                                    }
                                    emitter.complete();
                                })
                                .subscribe();
                    } catch (Exception e) {
                        log.error("chatStream setup failed: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                deepseekExecutor);

        return emitter;
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

        String userContext = buildUserContext(userId, null);
        String insight =
                callDeepSeekAsync("Generate a personalised daily health insight based on my recent data", userContext);
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
        String userContext = buildUserContext(userId, null);
        String insight =
                callDeepSeekAsync("Generate a personalised weekly health report based on my data", userContext);
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
        UUID userId = SecurityUtils.getCurrentUserId();
        String userContext = buildUserContext(userId, null);
        String prediction = callDeepSeekAsync(
                "Predict my energy level for tomorrow based on my recent sleep, activity and nutrition patterns",
                userContext);
        return InsightDto.builder()
                .type("energy_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getSymptomPrediction() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String userContext = buildUserContext(userId, null);
        String prediction = callDeepSeekAsync(
                "Analyze my symptom patterns and predict potential health issues I should watch for", userContext);
        return InsightDto.builder()
                .type("symptom_prediction")
                .content(prediction)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public InsightDto getRecommendations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String userContext = buildUserContext(userId, null);
        String recs = callDeepSeekAsync(
                "Give me personalised health recommendations for today based on my current data", userContext);
        return InsightDto.builder()
                .type("recommendations")
                .content(recs)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public InsightDto analyzeCorrelations() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String userContext = buildUserContext(userId, null);
        String analysis = callDeepSeekAsync(
                "Analyze correlations between my health metrics — sleep, activity, mood, nutrition", userContext);
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

    // ── User context ──────────────────────────────────────────────────────────

    /**
     * Fetches the user's health dashboard and profile from internal services and builds a
     * context string that is injected into the AI system prompt. Failures are non-fatal —
     * the AI will still respond, just without personalised data.
     */
    private String buildUserContext(UUID userId, String extraContext) {
        StringBuilder ctx = new StringBuilder();

        // Fetch dashboard data (water, steps, sleep, weight)
        try {
            String token = getInternalServiceToken();
            String dashboard = webClient
                    .get()
                    .uri(healthDataServiceUrl + "/api/v1/health/dashboard")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            if (dashboard != null) {
                ctx.append("USER HEALTH DATA TODAY:\n").append(dashboard).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("Could not fetch dashboard for user={}: {}", userId, e.getMessage());
        }

        // Fetch user profile (goals, weight target, timezone)
        try {
            String token = getInternalServiceToken();
            String profile = webClient
                    .get()
                    .uri(userServiceUrl + "/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            if (profile != null) {
                ctx.append("USER PROFILE:\n").append(profile).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("Could not fetch profile for user={}: {}", userId, e.getMessage());
        }

        if (extraContext != null && !extraContext.isBlank()) {
            ctx.append("ADDITIONAL CONTEXT:\n").append(extraContext).append("\n\n");
        }

        return ctx.toString();
    }

    /**
     * Generates a short-lived internal service token for the given user so the AI Coach
     * can call other microservices on their behalf. In production this should use a
     * dedicated service-to-service auth mechanism (e.g. mTLS or a service account JWT).
     * For now we re-use the user's identity from the SecurityContext.
     */
    private String getInternalServiceToken() {
        return SecurityUtils.getCurrentUserAccessToken();
    }

    private String buildSystemPrompt(String userContext) {
        String base = "You are HealthLife AI Coach, a health and wellness assistant. "
                + "Provide evidence-based, supportive health insights. "
                + "Never provide medical diagnoses. Always recommend consulting healthcare"
                + " professionals. Be concise and actionable.";
        if (userContext != null && !userContext.isBlank()) {
            return base + "\n\n" + userContext;
        }
        return base;
    }

    // ── DeepSeek API with retry ───────────────────────────────────────────────

    private String callDeepSeekAsync(String userMessage, String context) {
        try {
            return CompletableFuture.supplyAsync(() -> callDeepSeekWithRetry(userMessage, context), deepseekExecutor)
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
     * Calls DeepSeek with exponential backoff retry on 429 (rate limit) and 503 errors.
     * Max retries: 3. Initial backoff: 1s, doubles each attempt.
     */
    private String callDeepSeekWithRetry(String userMessage, String context) {
        long backoffMs = INITIAL_BACKOFF_MS;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callDeepSeekApi(userMessage, context);
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 429 || status == 503) {
                    log.warn(
                            "DeepSeek returned {} on attempt {}/{}, retrying in {}ms",
                            status,
                            attempt,
                            MAX_RETRIES,
                            backoffMs);
                    lastException = e;
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Request was interrupted. Please try again.";
                    }
                    backoffMs *= 2;
                } else {
                    // Non-retryable error
                    log.error("DeepSeek non-retryable error {}: {}", status, e.getMessage());
                    return "I'm currently unable to process your request. Please try again later.";
                }
            } catch (Exception e) {
                log.error("DeepSeek call failed on attempt {}: {}", attempt, e.getMessage());
                lastException = e;
            }
        }

        log.error(
                "DeepSeek failed after {} retries: {}",
                MAX_RETRIES,
                lastException != null ? lastException.getMessage() : "unknown");
        return "I'm currently unable to process your request. Please try again later.";
    }

    private String callDeepSeekApi(String userMessage, String context) {
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            log.warn("DeepSeek API key not configured");
            return "AI Coach is not configured. Please set the DEEPSEEK_API_KEY environment variable.";
        }
        try {
            String systemPrompt = buildSystemPrompt(context);
            Map<String, Object> requestMap = Map.of(
                    "model",
                    deepseekModel,
                    "max_tokens",
                    1024,
                    "messages",
                    List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)));

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

            return extractContent(rawResponse);
        } catch (WebClientResponseException e) {
            // Re-throw so retry logic can handle 429/503
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return "I'm currently unable to process your request. Please try again later.";
        }
    }

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
            log.warn("Failed to parse DeepSeek response: {}", e.getMessage());
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
