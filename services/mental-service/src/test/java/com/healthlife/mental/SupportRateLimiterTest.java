package com.healthlife.mental;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.mental.support.service.SupportRateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SupportRateLimiter covering:
 * - Requests within limit pass through
 * - Requests exceeding limit throw BadRequestException
 * - Different users have independent buckets
 * - configure() resets all buckets
 * - configure() with 0 defaults to 1
 */
class SupportRateLimiterTest {

    private SupportRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new SupportRateLimiter();
        rateLimiter.configure(5); // 5 requests per minute for tests
    }

    // ── within limit ──────────────────────────────────────────────────────────

    @Test
    void checkLimit_withinLimit_shouldNotThrow() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimiter.checkLimit(userId))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void checkLimit_firstRequest_shouldAlwaysPass() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> rateLimiter.checkLimit(userId))
                .doesNotThrowAnyException();
    }

    // ── exceeding limit ───────────────────────────────────────────────────────

    @Test
    void checkLimit_exceedingLimit_shouldThrowBadRequest() {
        UUID userId = UUID.randomUUID();

        // Exhaust the bucket
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit(userId);
        }

        // 6th request should fail
        assertThatThrownBy(() -> rateLimiter.checkLimit(userId))
                .isInstanceOf(BadRequestException.class);
    }

    // ── user isolation ────────────────────────────────────────────────────────

    @Test
    void checkLimit_differentUsers_shouldHaveIndependentBuckets() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // Exhaust user1's bucket
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit(user1);
        }

        // user2 should still be able to make requests
        assertThatCode(() -> rateLimiter.checkLimit(user2))
                .doesNotThrowAnyException();
    }

    @Test
    void checkLimit_user1Exhausted_user2StillWorks() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // Exhaust user1
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit(user1);
        }
        assertThatThrownBy(() -> rateLimiter.checkLimit(user1))
                .isInstanceOf(BadRequestException.class);

        // user2 unaffected
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimiter.checkLimit(user2))
                    .doesNotThrowAnyException();
        }
    }

    // ── configure ─────────────────────────────────────────────────────────────

    @Test
    void configure_shouldResetBuckets() {
        UUID userId = UUID.randomUUID();

        // Exhaust with limit=5
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit(userId);
        }
        assertThatThrownBy(() -> rateLimiter.checkLimit(userId))
                .isInstanceOf(BadRequestException.class);

        // Reconfigure — buckets are cleared
        rateLimiter.configure(10);

        // Should work again with new limit
        assertThatCode(() -> rateLimiter.checkLimit(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_zeroValue_shouldDefaultToOne() {
        rateLimiter.configure(0);
        UUID userId = UUID.randomUUID();

        // At least 1 request should pass
        assertThatCode(() -> rateLimiter.checkLimit(userId))
                .doesNotThrowAnyException();

        // 2nd request should fail (limit=1)
        assertThatThrownBy(() -> rateLimiter.checkLimit(userId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void configure_negativeValue_shouldDefaultToOne() {
        rateLimiter.configure(-5);
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> rateLimiter.checkLimit(userId))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.checkLimit(userId))
                .isInstanceOf(BadRequestException.class);
    }
}
