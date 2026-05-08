package com.healthlife.payment.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class StripeConfig {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(secretKey)) {
            log.warn("STRIPE_SECRET_KEY is not configured — payment endpoints will return 503");
            return;
        }
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialised");
    }
}
