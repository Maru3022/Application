package com.healthlife.payment;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.payment.dto.SubscriptionStatusResponse;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.healthlife.payment.service.PaymentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for PaymentService covering:
 * - getSubscriptionStatus returns FREE for new user
 * - getSubscriptionStatus returns existing subscription data
 * - createCheckoutSession throws when Stripe not configured
 * - createPortalSession throws when no subscription exists
 * - createPortalSession throws when no Stripe customer
 * - Subscription entity persists correctly
 * - Subscription status transitions
 */
@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        subscriptionRepository.deleteAll();
    }

    // ── getSubscriptionStatus ─────────────────────────────────────────────────

    @Test
    void getSubscriptionStatus_noSubscription_shouldReturnFree() {
        SubscriptionStatusResponse status = paymentService.getSubscriptionStatus();

        assertThat(status.getPlan()).isEqualTo("FREE");
        assertThat(status.getStatus()).isEqualTo("active");
        assertThat(status.getCurrentPeriodEnd()).isNull();
        assertThat(status.getCanceledAt()).isNull();
    }

    @Test
    void getSubscriptionStatus_existingSubscription_shouldReturnData() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeCustomerId("cus_test123")
                .stripeSubscriptionId("sub_test456")
                .build());

        SubscriptionStatusResponse status = paymentService.getSubscriptionStatus();

        assertThat(status.getPlan()).isEqualTo("PRO");
        assertThat(status.getStatus()).isEqualTo("active");
    }

    @Test
    void getSubscriptionStatus_canceledSubscription_shouldReturnCanceled() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FREE")
                .status("canceled")
                .canceledAt(java.time.OffsetDateTime.now().minusDays(1))
                .build());

        SubscriptionStatusResponse status = paymentService.getSubscriptionStatus();

        assertThat(status.getStatus()).isEqualTo("canceled");
        assertThat(status.getCanceledAt()).isNotNull();
    }

    @Test
    void getSubscriptionStatus_pastDueSubscription_shouldReturnPastDue() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("past_due")
                .build());

        SubscriptionStatusResponse status = paymentService.getSubscriptionStatus();

        assertThat(status.getStatus()).isEqualTo("past_due");
    }

    // ── createCheckoutSession — Stripe not configured ─────────────────────────

    @Test
    void createCheckoutSession_stripeNotConfigured_shouldThrowBadRequest() {
        // Stripe secret key is empty in test profile
        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_test123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void createCheckoutSession_emptyPriceId_stripeNotConfigured_shouldThrowBadRequest() {
        assertThatThrownBy(() -> paymentService.createCheckoutSession("")).isInstanceOf(BadRequestException.class);
    }

    // ── createPortalSession — no subscription ─────────────────────────────────

    @Test
    void createPortalSession_stripeNotConfigured_shouldThrowBadRequest() {
        assertThatThrownBy(() -> paymentService.createPortalSession())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void createPortalSession_noSubscription_stripeNotConfigured_shouldThrowBadRequest() {
        // No subscription in DB, Stripe not configured
        assertThatThrownBy(() -> paymentService.createPortalSession()).isInstanceOf(BadRequestException.class);
    }

    // ── Subscription entity ───────────────────────────────────────────────────

    @Test
    void subscription_defaultPlanIsFree() {
        Subscription sub = Subscription.builder().userId(userId).build();
        subscriptionRepository.save(sub);

        Subscription saved = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getPlan()).isEqualTo("FREE");
        assertThat(saved.getStatus()).isEqualTo("active");
    }

    @Test
    void subscription_findByStripeCustomerId() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .stripeCustomerId("cus_unique_123")
                .plan("PRO")
                .status("active")
                .build());

        assertThat(subscriptionRepository.findByStripeCustomerId("cus_unique_123"))
                .isPresent();
        assertThat(subscriptionRepository.findByStripeCustomerId("cus_nonexistent"))
                .isEmpty();
    }

    @Test
    void subscription_findByStripeSubscriptionId() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .stripeSubscriptionId("sub_unique_456")
                .plan("PRO")
                .status("active")
                .build());

        assertThat(subscriptionRepository.findByStripeSubscriptionId("sub_unique_456"))
                .isPresent();
        assertThat(subscriptionRepository.findByStripeSubscriptionId("sub_nonexistent"))
                .isEmpty();
    }

    @Test
    void subscription_uniquePerUser() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FREE")
                .status("active")
                .build());

        // Saving another subscription for the same user should fail (unique constraint on userId)
        Subscription duplicate = Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .build();

        assertThatThrownBy(() -> {
                    subscriptionRepository.save(duplicate);
                    subscriptionRepository.flush();
                })
                .isInstanceOf(Exception.class); // DataIntegrityViolationException or similar
    }

    // ── Multiple users ────────────────────────────────────────────────────────

    @Test
    void getSubscriptionStatus_differentUsers_shouldBeIsolated() {
        // User 1 has PRO
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .build());

        // User 2 has no subscription
        UUID user2 = UUID.randomUUID();
        var auth2 = new UsernamePasswordAuthenticationToken(
                user2, "user2@test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth2);

        SubscriptionStatusResponse status2 = paymentService.getSubscriptionStatus();
        assertThat(status2.getPlan()).isEqualTo("FREE");

        // Switch back to user 1
        var auth1 = new UsernamePasswordAuthenticationToken(
                userId, "user1@test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth1);

        SubscriptionStatusResponse status1 = paymentService.getSubscriptionStatus();
        assertThat(status1.getPlan()).isEqualTo("PRO");
    }
}
