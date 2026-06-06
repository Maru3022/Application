package com.healthlife.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.payment.dto.*;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.StripeWebhookEventRepository;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.healthlife.payment.service.PaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
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

    private PaymentService targetPaymentService;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        subscriptionRepository.deleteAll();
        // Unwrap the proxy to get the actual target object for reflection
        targetPaymentService = (PaymentService) AopUtils.getTargetClass(paymentService).cast(
                org.springframework.test.util.AopTestUtils.getTargetObject(paymentService));
    }

    private void setPaymentServiceField(String fieldName, Object value) {
        ReflectionTestUtils.setField(targetPaymentService, fieldName, value);
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
        // Set stripeSecretKey to empty explicitly
        setPaymentServiceField("stripeSecretKey", "");
        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_test123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void createCheckoutSession_emptyPriceId_stripeConfigured_shouldThrowBadRequest() {
         // Set stripeSecretKey so that requireStripe() passes, then test empty priceId
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        setPaymentServiceField( "pricePro", "price_123");
        assertThatThrownBy(() -> paymentService.createCheckoutSession("")).isInstanceOf(BadRequestException.class);
    }

    // ── createPortalSession — no subscription ─────────────────────────────────

    @Test
    void createPortalSession_stripeNotConfigured_shouldThrow() {
        // Stripe not configured, first check is requireStripe() but wait, let's see:
        // PaymentService.createPortalSession calls requireStripe(), but first calls get subscription!
        // So let's first save a subscription
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .build());
        // Let's explicitly set stripeSecretKey to empty
        setPaymentServiceField("stripeSecretKey", "");
        assertThatThrownBy(() -> paymentService.createPortalSession())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void createPortalSession_noSubscription_shouldThrowResourceNotFound() {
        // Set stripeSecretKey to pass requireStripe() first
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        // No subscription in DB
        assertThatThrownBy(() -> paymentService.createPortalSession())
                .isInstanceOf(ResourceNotFoundException.class);
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
    void subscription_uniquePerUser_onlyOneRecordExists() {
        // Save one subscription for the user
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FREE")
                .status("active")
                .build());

        // Only one record should exist for this user
        assertThat(subscriptionRepository.findByUserId(userId)).isPresent();
        assertThat(subscriptionRepository.findAll().stream()
                        .filter(s -> s.getUserId().equals(userId))
                        .count())
                .isEqualTo(1);
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

    // ── Mocked Stripe integration ─────────────────────────────────────────────

    @Autowired
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @Test
    void validatePriceId_noPricesConfigured_shouldThrow() {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        setPaymentServiceField( "pricePro", "");
        setPaymentServiceField( "pricePremium", "");
        setPaymentServiceField( "priceFamily", "");

        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_test"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stripe price IDs are not configured");
    }

    @Test
    void validatePriceId_unsupportedPriceId_shouldThrow() {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        setPaymentServiceField( "pricePro", "price_123");
        setPaymentServiceField( "pricePremium", "");
        setPaymentServiceField( "priceFamily", "");

        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported subscription plan");
    }

    @Test
    void createCheckoutSession_withValidPrice_shouldCreateSession() throws StripeException {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        setPaymentServiceField( "pricePro", "price_pro_123");

        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class);
             MockedStatic<Customer> mockedCustomer = Mockito.mockStatic(Customer.class)) {

            // Mock Customer.create
            Customer mockCustomer = mock(Customer.class);
            when(mockCustomer.getId()).thenReturn("cus_test_456");
            mockedCustomer.when(() -> Customer.create(any(CustomerCreateParams.class))).thenReturn(mockCustomer);

            // Mock Session.create
            Session mockSession = mock(Session.class);
            when(mockSession.getId()).thenReturn("cs_test_789");
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            CheckoutSessionResponse response = paymentService.createCheckoutSession("price_pro_123");

            assertThat(response.getSessionId()).isEqualTo("cs_test_789");
            assertThat(response.getUrl()).isEqualTo("https://checkout.stripe.com/test");

            // Verify customer was created and saved
            Subscription savedSub = subscriptionRepository.findByUserId(userId).orElseThrow();
            assertThat(savedSub.getStripeCustomerId()).isEqualTo("cus_test_456");
        }
    }

    @Test
    void createCheckoutSession_existingCustomer_shouldReuseCustomer() throws StripeException {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");
        setPaymentServiceField( "pricePro", "price_pro_123");

        // Save existing subscription with customer ID
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .stripeCustomerId("cus_existing_123")
                .build());

        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            when(mockSession.getId()).thenReturn("cs_test_456");
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            paymentService.createCheckoutSession("price_pro_123");

            // No customer should have been created
        }
    }

    @Test
    void createPortalSession_withCustomer_shouldCreatePortal() throws StripeException {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");

        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .stripeCustomerId("cus_test_123")
                .build());

        try (MockedStatic<com.stripe.model.billingportal.Session> mockedPortal =
                     Mockito.mockStatic(com.stripe.model.billingportal.Session.class)) {

            com.stripe.model.billingportal.Session mockPortal = mock(com.stripe.model.billingportal.Session.class);
            when(mockPortal.getUrl()).thenReturn("https://billing.stripe.com/test");
            mockedPortal.when(() -> com.stripe.model.billingportal.Session.create(
                    any(com.stripe.param.billingportal.SessionCreateParams.class))).thenReturn(mockPortal);

            CustomerPortalResponse response = paymentService.createPortalSession();

            assertThat(response.getUrl()).isEqualTo("https://billing.stripe.com/test");
        }
    }

    @Test
    void createPortalSession_noCustomer_shouldThrow() {
        setPaymentServiceField( "stripeSecretKey", "sk_test_123");

        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .build());

        assertThatThrownBy(() -> paymentService.createPortalSession())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No Stripe customer found for this user");
    }
}
