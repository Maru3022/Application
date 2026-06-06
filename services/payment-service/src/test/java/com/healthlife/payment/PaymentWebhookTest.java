package com.healthlife.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.payment.entity.StripeWebhookEvent;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.StripeWebhookEventRepository;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.healthlife.payment.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
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
 * Tests for PaymentService webhook handling covering:
 * - Duplicate webhook events are idempotently ignored
 * - Unknown event types are silently ignored
 * - handleWebhook saves event to deduplication table
 * - Subscription status transitions via webhook
 */
@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class PaymentWebhookTest {

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
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        stripeWebhookEventRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void handleWebhook_duplicateEvent_shouldBeIgnored() {
        // Pre-save the event as already processed
        stripeWebhookEventRepository.save(StripeWebhookEvent.builder()
                .eventId("evt_duplicate_001")
                .eventType("customer.subscription.updated")
                .build());

        Event event = buildEvent("evt_duplicate_001", "customer.subscription.updated");

        // Should not throw and should not process again
        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();

        // Only 1 record should exist (the pre-saved one)
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(1);
    }

    // ── unknown event type ────────────────────────────────────────────────────

    @Test
    void handleWebhook_unknownEventType_shouldBeIgnoredGracefully() {
        Event event = buildEvent("evt_unknown_001", "payment.intent.created");

        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();

        // Event should still be saved for deduplication
        assertThat(stripeWebhookEventRepository.existsById("evt_unknown_001")).isTrue();
    }

    // ── event persistence ─────────────────────────────────────────────────────

    @Test
    void handleWebhook_newEvent_shouldSaveToDeduplicationTable() {
        Event event = buildEvent("evt_new_001", "invoice.payment_failed");

        paymentService.handleWebhook(event);

        assertThat(stripeWebhookEventRepository.existsById("evt_new_001")).isTrue();
    }

    @Test
    void handleWebhook_multipleUniqueEvents_shouldSaveAll() {
        paymentService.handleWebhook(buildEvent("evt_001", "invoice.payment_failed"));
        paymentService.handleWebhook(buildEvent("evt_002", "invoice.payment_failed"));
        paymentService.handleWebhook(buildEvent("evt_003", "customer.subscription.deleted"));

        assertThat(stripeWebhookEventRepository.count()).isEqualTo(3);
    }

    // ── subscription status transitions ──────────────────────────────────────

    @Test
    void handleWebhook_subscriptionDeleted_shouldUpdateStatus() {
        // Create an existing subscription
        Subscription sub = subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_test_delete_001")
                .build());

        // Build a subscription.deleted event with a mock Stripe subscription
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_test_delete_001");
        when(stripeSub.getStatus()).thenReturn("canceled");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_sub_deleted_001");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        paymentService.handleWebhook(event);

        Subscription updated = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("canceled");
        assertThat(updated.getCanceledAt()).isNotNull();
    }

    @Test
    void handleWebhook_checkoutCompleted_shouldActivateSubscription() {
        // Mock checkout session
        com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
        when(session.getCustomer()).thenReturn("cus_test123");
        when(session.getSubscription()).thenReturn("sub_test123");
        when(session.getMetadata()).thenReturn(java.util.Map.of("userId", userId.toString()));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_checkout_001");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        paymentService.handleWebhook(event);

        Subscription updated = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getStripeCustomerId()).isEqualTo("cus_test123");
        assertThat(updated.getStripeSubscriptionId()).isEqualTo("sub_test123");
        assertThat(updated.getStatus()).isEqualTo("active");
    }

    @Test
    void handleWebhook_subscriptionUpdated_shouldUpdateDetails() {
        // Create existing subscription
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FREE")
                .status("active")
                .stripeSubscriptionId("sub_test_update_001")
                .build());

        // Mock Stripe subscription
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_test_update_001");
        when(stripeSub.getStatus()).thenReturn("active");
        when(stripeSub.getCurrentPeriodStart()).thenReturn(1736265600L);
        when(stripeSub.getCurrentPeriodEnd()).thenReturn(1738857600L);

        com.stripe.model.SubscriptionItem item = mock(com.stripe.model.SubscriptionItem.class);
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        when(price.getId()).thenReturn("price_pro_123");
        when(item.getPrice()).thenReturn(price);
        com.stripe.model.SubscriptionItemCollection items = mock(com.stripe.model.SubscriptionItemCollection.class);
        when(items.getData()).thenReturn(List.of(item));
        when(stripeSub.getItems()).thenReturn(items);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_sub_updated_001");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        paymentService.handleWebhook(event);

        Subscription updated = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getStripePriceId()).isEqualTo("price_pro_123");
        assertThat(updated.getCurrentPeriodStart()).isNotNull();
        assertThat(updated.getCurrentPeriodEnd()).isNotNull();
    }

    @Test
    void handleWebhook_paymentFailed_shouldUpdateStatus() {
        // Create existing subscription
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_test_payment_failed_001")
                .build());

        // Mock invoice
        com.stripe.model.Invoice invoice = mock(com.stripe.model.Invoice.class);
        when(invoice.getSubscription()).thenReturn("sub_test_payment_failed_001");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_payment_failed_001");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        paymentService.handleWebhook(event);

        Subscription updated = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("past_due");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Event buildEvent(String id, String type) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }
}
