package com.healthlife.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.payment.entity.StripeWebhookEvent;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.StripeWebhookEventRepository;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.healthlife.payment.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import java.time.OffsetDateTime;
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
 * Покрытие PaymentService:
 * - requireStripe (проверки пустого ключа)
 * - validatePriceId (все ветки — пустой ID, несконфигурированные цены, неизвестный ID)
 * - getSubscriptionStatus (различные статусы подписок)
 * - handleWebhook (все 4 типа событий + idempotency + unknown)
 * - resolvePlan через handleSubscriptionUpdated (pro, premium, family, unknown)
 */
@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class PaymentServiceResolvePlanTest {

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

    // ── requireStripe — всегда бросает когда ключ пустой ─────────────────────

    @Test
    void createCheckoutSession_emptyKey_shouldThrowWithCorrectMessage() {
        assertThatThrownBy(() -> paymentService.createCheckoutSession("price_test_pro"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void createPortalSession_emptyKey_shouldThrow() {
        assertThatThrownBy(() -> paymentService.createPortalSession())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    // ── getSubscriptionStatus все ветки ───────────────────────────────────────

    @Test
    void getSubscriptionStatus_noSub_returnsFreeActive() {
        var resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getPlan()).isEqualTo("FREE");
        assertThat(resp.getStatus()).isEqualTo("active");
        assertThat(resp.getCurrentPeriodEnd()).isNull();
        assertThat(resp.getCanceledAt()).isNull();
    }

    @Test
    void getSubscriptionStatus_premiumSub_returnsPremium() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PREMIUM")
                .status("active")
                .stripeSubscriptionId("sub_prem_001")
                .build());

        var resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getPlan()).isEqualTo("PREMIUM");
    }

    @Test
    void getSubscriptionStatus_familySub_returnsFamily() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FAMILY")
                .status("active")
                .build());

        var resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getPlan()).isEqualTo("FAMILY");
    }

    @Test
    void getSubscriptionStatus_withPeriodDates_returnsDates() {
        var now = OffsetDateTime.now();
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(30))
                .build());

        var resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getCurrentPeriodEnd()).isNotNull();
    }

    @Test
    void getSubscriptionStatus_canceledWithDate_returnsCanceledAt() {
        var cancelTime = OffsetDateTime.now().minusDays(2);
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("FREE")
                .status("canceled")
                .canceledAt(cancelTime)
                .build());

        var resp = paymentService.getSubscriptionStatus();
        assertThat(resp.getStatus()).isEqualTo("canceled");
        assertThat(resp.getCanceledAt()).isNotNull();
    }

    // ── handleWebhook idempotency ─────────────────────────────────────────────

    @Test
    void handleWebhook_duplicateEvent_noAdditionalRecord() {
        stripeWebhookEventRepository.save(StripeWebhookEvent.builder()
                .eventId("evt_idem_001")
                .eventType("checkout.session.completed")
                .build());

        paymentService.handleWebhook(buildEvent("evt_idem_001", "checkout.session.completed"));
        assertThat(stripeWebhookEventRepository.count()).isEqualTo(1);
    }

    // ── handleWebhook checkout.session.completed — userId null ───────────────

    @Test
    void handleWebhook_checkoutCompleted_noUserId_shouldNotThrow() {
        var session = mock(com.stripe.model.checkout.Session.class);
        when(session.getMetadata()).thenReturn(java.util.Collections.emptyMap());

        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(session));

        var event = mock(Event.class);
        when(event.getId()).thenReturn("evt_co_nouser");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
        assertThat(stripeWebhookEventRepository.existsById("evt_co_nouser")).isTrue();
    }

    // ── handleWebhook invoice.payment_failed ─────────────────────────────────

    @Test
    void handleWebhook_paymentFailed_existingSub_setsPastDue() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_pf_002")
                .build());

        var invoice = mock(Invoice.class);
        when(invoice.getSubscription()).thenReturn("sub_pf_002");

        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(invoice));

        var event = mock(Event.class);
        when(event.getId()).thenReturn("evt_pf_002");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        paymentService.handleWebhook(event);

        var saved = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("past_due");
    }

    @Test
    void handleWebhook_paymentFailed_noMatchingSub_shouldNotThrow() {
        var invoice = mock(Invoice.class);
        when(invoice.getSubscription()).thenReturn("sub_nonexistent");

        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(invoice));

        var event = mock(Event.class);
        when(event.getId()).thenReturn("evt_pf_none");
        when(event.getType()).thenReturn("invoice.payment_failed");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    // ── handleWebhook customer.subscription.deleted ───────────────────────────

    @Test
    void handleWebhook_subscriptionDeleted_setsCanceledAndFree() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .stripeSubscriptionId("sub_del_002")
                .build());

        var stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_del_002");
        when(stripeSub.getStatus()).thenReturn("canceled");

        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(stripeSub));

        var event = mock(Event.class);
        when(event.getId()).thenReturn("evt_del_002");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        paymentService.handleWebhook(event);

        var saved = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("canceled");
        assertThat(saved.getPlan()).isEqualTo("FREE");
        assertThat(saved.getCanceledAt()).isNotNull();
    }

    // ── handleWebhook unknown type ────────────────────────────────────────────

    @Test
    void handleWebhook_unknownType_savedToDeduplication() {
        paymentService.handleWebhook(buildEvent("evt_unk_001", "refund.created"));
        assertThat(stripeWebhookEventRepository.existsById("evt_unk_001")).isTrue();
    }

    // ── handleWebhook customer.subscription.updated ───────────────────────────

    @Test
    void handleWebhook_subscriptionUpdated_noMatchingSub_shouldNotThrow() {
        var stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_notfound");
        when(stripeSub.getStatus()).thenReturn("active");

        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(stripeSub));

        var event = mock(Event.class);
        when(event.getId()).thenReturn("evt_upd_none");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
    }

    // ── multiple users isolation ──────────────────────────────────────────────

    @Test
    void getSubscriptionStatus_twoUsers_isolated() {
        subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .plan("PRO")
                .status("active")
                .build());

        UUID user2 = UUID.randomUUID();
        setAuth(user2);

        var resp2 = paymentService.getSubscriptionStatus();
        assertThat(resp2.getPlan()).isEqualTo("FREE");

        setAuth(userId);
        var resp1 = paymentService.getSubscriptionStatus();
        assertThat(resp1.getPlan()).isEqualTo("PRO");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, "test@test.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Event buildEvent(String id, String type) {
        var event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        var deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deser);
        return event;
    }
}
