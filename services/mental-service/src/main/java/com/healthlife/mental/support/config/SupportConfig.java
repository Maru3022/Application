package com.healthlife.mental.support.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Конфигурация службы поддержки (Claude API + WebClient). */
@Configuration
@EnableConfigurationProperties(SupportProperties.class)
public class SupportConfig {

    @Bean
    WebClient anthropicWebClient(SupportProperties properties) {
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", properties.getAnthropic().getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }
}
