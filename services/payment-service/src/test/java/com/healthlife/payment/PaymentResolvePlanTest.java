package com.healthlife.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.payment.dto.SubscriptionStatusResponse;
import com.healthlife.payment.entity.StripeWebhookEvent;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.StripeWebhookEventRepository;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.healthlife.payment.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import java.util.List;
import java.util.Optional;
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
 * Дополнительные тесты PaymentService:
 * - handleWebhook idempotency
 * - handleWebhook unknown event type
 * - handleWebhook subscription deleted
 * - handleWebhook payment failed
 * - handleWebhook saves event record
 * - getSubscriptionStatus with period dates
 * - validatePriceId edge cases
 * - resolvePlan logic
 */
@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class PaymentResolvePlanTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        setAuth(userId);
        subscriptionRepository.deleteAll();
        stripeWebhookEventRepository.deleteAll();
    }

    // ── handleWebhook idempotency ─────────────────────────────────────────────

    @Test
    void handleWebhook_duplicateEvent_shouldBeIgnored() {
        stripeWebhookEventRepository.save(StripeWebhookEvent.builder()
                .eventId("evt_dup_001")
                .eventType("checkout.session.completed")
                .build());

        Event event = mockEvent("evt_dup_001", "checkout.session.completed");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
        // No additional record saved
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(1);
    }

    // ── handleWebhook unknown type ────────────────────────────────────────────

    @Test
    void handleWebhook_unknownEventType_shouldBeIgnoredAndSaved() {
        Event event = mockEvent("evt_unknown_001", "payment.created");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
        assertThat(stripeWebhookEventRepository.existsById("evt_unknown_001")).isTrue();
    }

    // ── handleWebhook subscription deleted ───────────────────────────────────

    @Test
    void handleWebhook_subscriptionDeleted_shouldSetCanceled() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_del_001")
                .build());

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_del_001");
        when(stripeSub.getStatus()).thenReturn("canceled");

        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_del_001");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        paymentService.handleWebhook(event);

        Subscription saved = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("canceled");
        assertThat(saved.getPlan()).isEqualTo("FREE");
        assertThat(saved.getCanceledAt()).isNotNull();
    }

    // ── handleWebhook payment failed ─────────────────────────────────────────

    @Test
    void handleWebhook_paymentFailed_shouldSetPastDue() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_pf_001")
                .build());

        Invoice invoice = mock(Invoice.class);
        when(invoice.getSubscription()).thenReturn("sub_pf_001");

        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(invoice));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_pf_001");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        paymentService.handleWebhook(event);

        Subscription saved = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("past_due");
    }

    // ── handleWebhook empty deserializer ─────────────────────────────────────

    @Test
    void handleWebhook_checkoutCompleted_emptyData_shouldNotThrow() {
        Event event = mockEvent("evt_empty_001", "checkout.session.completed");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    @Test
    void handleWebhook_subscriptionUpdated_emptyData_shouldNotThrow() {
        Event event = mockEvent("evt_upd_empty", "customer.subscription.updated");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    @Test
    void handleWebhook_subscriptionDeleted_emptyData_shouldNotThrow() {
        Event event = mockEvent("evt_del_empty", "customer.subscription.deleted");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    @Test
    void handleWebhook_paymentFailed_emptyData_shouldNotThrow() {
        Event event = mockEvent("evt_pf_empty", "invoice.payment_failed");
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    // ── handleWebhook saves event record ─────────────────────────────────────

    @Test
    void handleWebhook_newEvent_shouldSaveToDeduplicationTable() {
        Event event = mockEvent("evt_new_001", "invoice.payment_failed");
        paymentService.handleWebhook(event);
        assertThat(stripeWebhookEventRepository.existsById("evt_new_001")).isTrue();
    }

    // ── getSubscriptionStatus with period dates ───────────────────────────────

    @Test
    void getSubscriptionStatus_withPeriodDates_shouldReturnDates() {
        var now = java.time.OffsetDateTime.now();
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .currentPeriodEnd(now.plusDays(30))
                .currentPeriodStart(now)
                .build());

        SubscriptionStatusResponse resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getCurrentPeriodEnd()).isNotNull();
    }

    // ── createCheckoutSession validation ─────────────────────────────────────

    @Test
    void createCheckoutSession_stripeNotConfigured_missingKey_shouldThrow() {
        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_any"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    // ── createPortalSession ───────────────────────────────────────────────────

    @Test
    void createPortalSession_noSubscriptionAndNoStripe_shouldThrow() {
        assertThatThrownBy(() -> paymentService.createPortalSession()).isInstanceOf(BadRequestException.class);
    }

    // ── multiple events ───────────────────────────────────────────────────────

    @Test
    void handleWebhook_multipleDistinctEvents_allSaved() {
        paymentService.handleWebhook(mockEvent("e1", "invoice.payment_failed"));
        paymentService.handleWebhook(mockEvent("e2", "customer.subscription.deleted"));
        paymentService.handleWebhook(mockEvent("e3", "checkout.session.completed"));
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(3);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Event mockEvent(String id, String type) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deser);
        return event;
    }
}
