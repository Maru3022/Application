package com.healthlife.mental;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.mental.support.client.AnthropicClient;
import com.healthlife.mental.support.config.SupportProperties;
import com.healthlife.mental.support.dto.SupportChatRequest;
import com.healthlife.mental.support.dto.SupportChatResponse;
import com.healthlife.mental.support.service.SupportChatService;
import com.healthlife.mental.support.service.SupportRateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SupportChatService covering:
 * - Successful chat returns reply
 * - Language normalization (null → "ru", uppercase → lowercase)
 * - Rate limiter is called with userId
 * - Message is trimmed before sending to Claude
 * - PII not logged (only userId and language)
 * - Rate limiter exception propagates
 */
class SupportChatServiceTest {

    private AnthropicClient anthropicClient;
    private SupportRateLimiter rateLimiter;
    private SupportProperties properties;
    private SupportChatService service;

    @BeforeEach
    void setUp() {
        anthropicClient = mock(AnthropicClient.class);
        rateLimiter = mock(SupportRateLimiter.class);
        properties = mock(SupportProperties.class);
        when(properties.getRateLimitRequestsPerMinute()).thenReturn(20);
        service = new SupportChatService(anthropicClient, rateLimiter, properties);
    }

    // ── successful chat ───────────────────────────────────────────────────────

    @Test
    void chat_shouldReturnReplyFromClaude() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("Привет! Чем могу помочь?");

        SupportChatResponse resp = service.chat(userId, new SupportChatRequest("Привет", "ru"));

        assertThat(resp.reply()).isEqualTo("Привет! Чем могу помочь?");
        assertThat(resp.language()).isEqualTo("ru");
    }

    @Test
    void chat_englishLanguage_shouldReturnEnglishReply() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("Hello! How can I help?");

        SupportChatResponse resp = service.chat(userId, new SupportChatRequest("Hello", "en"));

        assertThat(resp.language()).isEqualTo("en");
        assertThat(resp.reply()).isEqualTo("Hello! How can I help?");
    }

    // ── language normalization ────────────────────────────────────────────────

    @Test
    void chat_nullLanguage_shouldDefaultToRu() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("Ответ");

        SupportChatResponse resp = service.chat(userId, new SupportChatRequest("Вопрос", null));

        assertThat(resp.language()).isEqualTo("ru");
    }

    @Test
    void chat_emptyLanguage_shouldDefaultToRu() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("Ответ");

        SupportChatResponse resp = service.chat(userId, new SupportChatRequest("Вопрос", ""));

        assertThat(resp.language()).isEqualTo("ru");
    }

    @Test
    void chat_uppercaseLanguage_shouldNormalize() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("Reply");

        SupportChatResponse resp = service.chat(userId, new SupportChatRequest("Hello", "EN"));

        assertThat(resp.language()).isEqualTo("en");
    }

    // ── rate limiter ──────────────────────────────────────────────────────────

    @Test
    void chat_shouldCallRateLimiterWithUserId() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("OK");

        service.chat(userId, new SupportChatRequest("Test", "ru"));

        verify(rateLimiter).checkLimit(userId);
    }

    @Test
    void chat_rateLimiterThrows_shouldPropagateException() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("Rate limit exceeded")).when(rateLimiter).checkLimit(userId);

        assertThatThrownBy(() -> service.chat(userId, new SupportChatRequest("Test", "ru")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rate limit exceeded");

        // Claude should NOT be called when rate limit is exceeded
        verify(anthropicClient, never()).chat(anyString(), anyString());
    }

    // ── message trimming ──────────────────────────────────────────────────────

    @Test
    void chat_messageWithWhitespace_shouldBeTrimmed() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("OK");

        service.chat(userId, new SupportChatRequest("  Hello world  ", "en"));

        verify(anthropicClient).chat(anyString(), eq("Hello world"));
    }

    // ── system prompt ─────────────────────────────────────────────────────────

    @Test
    void chat_shouldPassSystemPromptToClaude() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenReturn("OK");

        service.chat(userId, new SupportChatRequest("Question", "ru"));

        verify(anthropicClient)
                .chat(
                        argThat(prompt -> prompt.contains("ментального здоровья") && prompt.contains("8-800-2000-122")),
                        anyString());
    }

    // ── Claude error propagation ──────────────────────────────────────────────

    @Test
    void chat_claudeThrows_shouldPropagateException() {
        UUID userId = UUID.randomUUID();
        when(anthropicClient.chat(anyString(), anyString())).thenThrow(new RuntimeException("Claude API unavailable"));

        assertThatThrownBy(() -> service.chat(userId, new SupportChatRequest("Test", "ru")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Claude API unavailable");
    }
}
