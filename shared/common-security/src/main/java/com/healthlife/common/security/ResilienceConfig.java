package com.healthlife.common.security;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Resilience4j configuration beans used across microservices. Defines sensible defaults for
 * circuit breakers and time limiters that protect inter-service and external HTTP calls.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Default circuit breaker configuration: 50% failure rate threshold, 10-second sliding window,
     * 30-second wait in open state, and slow-call detection at 3 seconds.
     */
    @Bean
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(java.io.IOException.class, java.util.concurrent.TimeoutException.class)
                .build();
    }

    /** Default time limiter: fail fast if a call exceeds 5 seconds. */
    @Bean
    public TimeLimiterConfig defaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build();
    }
}
