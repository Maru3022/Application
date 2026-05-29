package com.healthlife.mental.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки Claude API для службы поддержки. */
@ConfigurationProperties(prefix = "support")
public class SupportProperties {

    private final Anthropic anthropic = new Anthropic();
    private int rateLimitRequestsPerMinute = 20;

    public Anthropic getAnthropic() {
        return anthropic;
    }

    public int getRateLimitRequestsPerMinute() {
        return rateLimitRequestsPerMinute;
    }

    public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
    }

    public static class Anthropic {
        private String apiKey = "";
        private String model = "claude-sonnet-4-20250514";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
