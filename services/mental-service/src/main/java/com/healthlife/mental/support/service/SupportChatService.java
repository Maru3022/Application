package com.healthlife.mental.support.service;

import com.healthlife.mental.support.client.AnthropicClient;
import com.healthlife.mental.support.config.SupportProperties;
import com.healthlife.mental.support.dto.SupportChatRequest;
import com.healthlife.mental.support.dto.SupportChatResponse;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Служба поддержки на базе Claude API. */
@Service
@RequiredArgsConstructor
public class SupportChatService {

    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);

    private static final String SYSTEM_PROMPT =
            """
            Ты — тёплый, эмпатичный агент поддержки мобильного приложения для ментального здоровья и медитаций. Отвечай на том языке, на котором пишет пользователь (русский или английский).

            Ты помогаешь с:
            - Техническими вопросами: вход, аккаунт, подписка, баги, уведомления
            - Контентом приложения: типы медитаций, программы, функции
            - Общими вопросами о mindfulness и дыхательных практиках

            Ты НЕ делаешь:
            - Не ставишь диагнозы и не заменяешь психолога
            - Не рекомендуешь медикаменты
            - Не занимаешься оценкой рисков при кризисных ситуациях

            Если пользователь выражает серьёзный дистресс или мысли о самоповреждении — ответь с заботой и немедленно предоставь: 🇷🇺 Россия: 8-800-2000-122 | 🌍 Международный: findahelpline.com

            Если вопрос технически сложный — сообщи что передаёшь в команду поддержки и попроси email.

            Тон: спокойный, тёплый, без корпоративных клише. Короткие абзацы — удобно читать на телефоне. На русском используй 'ты'.""";

    private final AnthropicClient anthropicClient;
    private final SupportRateLimiter rateLimiter;
    private final SupportProperties properties;

    @PostConstruct
    void initRateLimiter() {
        rateLimiter.configure(properties.getRateLimitRequestsPerMinute());
    }

    public SupportChatResponse chat(UUID userId, SupportChatRequest request) {
        rateLimiter.checkLimit(userId);

        String language = normalizeLanguage(request.language());
        // Логируем только идентификатор и время — без текста сообщения (PII)
        log.info("Support chat request userId={} language={}", userId, language);

        String reply = anthropicClient.chat(SYSTEM_PROMPT, request.message().trim());
        return new SupportChatResponse(reply, language);
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return "ru";
        }
        return language.toLowerCase(Locale.ROOT);
    }
}
