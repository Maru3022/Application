package com.healthlife.mental.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Запрос в службу поддержки (только текст сообщения, без PII). */
public record SupportChatRequest(
        @NotBlank(message = "{support.message.required}") @Size(max = 1000, message = "{support.message.max}")
                String message,
        @Pattern(regexp = "ru|en", message = "{support.language.invalid}") String language) {}
