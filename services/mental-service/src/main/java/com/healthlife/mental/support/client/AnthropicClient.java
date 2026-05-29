package com.healthlife.mental.support.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.mental.support.config.SupportProperties;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Клиент Anthropic Messages API (передаётся только текст сообщения пользователя). */
@Component
public class AnthropicClient {

    private final WebClient webClient;
    private final SupportProperties properties;

    public AnthropicClient(WebClient anthropicWebClient, SupportProperties properties) {
        this.webClient = anthropicWebClient;
        this.properties = properties;
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(properties.getAnthropic().getApiKey())) {
            throw new BadRequestException("support.not.configured");
        }

        var request = new MessageRequest(
                properties.getAnthropic().getModel(), 1024, systemPrompt, List.of(new Message("user", userMessage)));

        try {
            MessageResponse response = webClient
                    .post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MessageResponse.class)
                    .block();

            if (response == null || response.content == null || response.content.isEmpty()) {
                throw new BadRequestException("support.empty.response");
            }
            return response.content.stream()
                    .filter(block -> block.text != null && !block.text.isBlank())
                    .map(block -> block.text)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("support.empty.response"));
        } catch (WebClientResponseException e) {
            throw new BadRequestException("support.unavailable");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageRequest(String model, int max_tokens, String system, List<Message> messages) {}

    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MessageResponse {
        List<ContentBlock> content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ContentBlock {
        @JsonProperty("type")
        String type;

        String text;
    }
}
