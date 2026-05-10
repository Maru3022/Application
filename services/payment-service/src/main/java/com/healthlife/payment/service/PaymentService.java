package com.healthlife.payment.service;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.payment.dto.*;
import com.healthlife.payment.entity.StripeWebhookEvent;
import com.healthlife.payment.entity.Subscription;
import com.healthlife.payment.repository.StripeWebhookEventRepository;
import com.healthlife.payment.repository.SubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SubscriptionRepository subscriptionRepository;
    private final StripeWebhookEventRepository stripeWebhookEventRepository;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.success-url:https://app.healthlife.com/subscription/success}")
    private String successUrl;

    @Value("${stripe.cancel-url:https://app.healthlife.com/subscription/cancel}")
    private String cancelUrl;

    @Value("${stripe.price-pro:}")
    private String pricePro;

    @Value("${stripe.price-premium:}")
    private String pricePremium;

    @Value("${stripe.price-family:}")
    private String priceFamily;

    // ── Checkout ─────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe Checkout Session for a new subscription.
     * Returns the session URL to redirect the user to Stripe's hosted checkout page.
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String priceId) {
        requireStripe();
        validatePriceId(priceId);
        UUID userId = SecurityUtils.getCurrentUserId();

        // Get or create Stripe customer
        Subscription sub = subscriptionRepository
                .findByUserId(userId)
                .orElseGet(() -> Subscription.builder().userId(userId).build());

        String customerId = sub.getStripeCustomerId();
        if (!StringUtils.hasText(customerId)) {
            customerId = createStripeCustomer(userId);
            sub.setStripeCustomerId(customerId);
            subscriptionRepository.save(sub);
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .putMetadata("userId", userId.toString())
                    .build();

            Session session = Session.create(params);
            log.info("Checkout session created for user={} priceId={}", userId, priceId);
            return CheckoutSessionResponse.builder()
                    .sessionId(session.getId())
                    .url(session.getUrl())
                    .build();
        } catch (StripeException e) {
            log.error("Failed to create checkout session for user={}: {}", userId, e.getMessage());
            throw new BadRequestException("Failed to create checkout session: " + e.getMessage());
        }
    }

    // ── Portal ────────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe Customer Portal session so the user can manage their subscription
     * (update payment method, cancel, view invoices) without leaving the app.
     */
    public CustomerPortalResponse createPortalSession() {
        requireStripe();
        UUID userId = SecurityUtils.getCurrentUserId();

        Subscription sub = subscriptionRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "userId", userId));

        if (!StringUtils.hasText(sub.getStripeCustomerId())) {
            throw new BadRequestException("No Stripe customer found for this user");
        }

        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(sub.getStripeCustomerId())
                            .setReturnUrl("https://app.healthlife.com/profile")
                            .build();

            com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);

            return CustomerPortalResponse.builder().url(session.getUrl()).build();
        } catch (StripeException e) {
            log.error("Failed to create portal session for user={}: {}", userId, e.getMessage());
            throw new BadRequestException("Failed to create portal session: " + e.getMessage());
        }
    }

    // ── Subscription status ───────────────────────────────────────────────────

    public SubscriptionStatusResponse getSubscriptionStatus() {
        UUID userId = SecurityUtils.getCurrentUserId();
        Subscription sub = subscriptionRepository
                .findByUserId(userId)
                .orElse(Subscription.builder()
                        .userId(userId)
                        .plan("FREE")
                        .status("active")
                        .build());

        return SubscriptionStatusResponse.builder()
                .plan(sub.getPlan())
                .status(sub.getStatus())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .canceledAt(sub.getCanceledAt())
                .build();
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    /**
     * Handles Stripe webhook events. Called by {@code PaymentController} after signature
     * verification. Keeps the local subscription record in sync with Stripe.
     */
    @Transactional
    public void handleWebhook(Event event) {
        log.info("Stripe webhook received: type={} id={}", event.getType(), event.getId());
        if (stripeWebhookEventRepository.existsById(event.getId())) {
            log.info("Stripe webhook already processed: id={}", event.getId());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
        stripeWebhookEventRepository.save(StripeWebhookEvent.builder()
                .eventId(event.getId())
                .eventType(event.getType())
                .build());
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BadRequestException("Cannot deserialise checkout session"));

        String userId = session.getMetadata().get("userId");
        if (userId == null) return;

        Subscription sub = subscriptionRepository
                .findByUserId(UUID.fromString(userId))
                .orElse(Subscription.builder().userId(UUID.fromString(userId)).build());

        sub.setStripeCustomerId(session.getCustomer());
        sub.setStripeSubscriptionId(session.getSubscription());
        sub.setStatus("active");
        subscriptionRepository.save(sub);
        log.info("Subscription activated for userId={}", userId);
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription)
                event.getDataObjectDeserializer().getObject().orElseThrow();

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus(stripeSub.getStatus());
            sub.setStripePriceId(
                    stripeSub.getItems().getData().get(0).getPrice().getId());
            sub.setPlan(resolvePlan(sub.getStripePriceId()));
            if (stripeSub.getCurrentPeriodStart() != null) {
                sub.setCurrentPeriodStart(OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneOffset.UTC));
            }
            if (stripeSub.getCurrentPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneOffset.UTC));
            }
            subscriptionRepository.save(sub);
            log.info("Subscription updated: id={} status={}", stripeSub.getId(), stripeSub.getStatus());
        });
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription)
                event.getDataObjectDeserializer().getObject().orElseThrow();

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus("canceled");
            sub.setPlan("FREE");
            sub.setCanceledAt(OffsetDateTime.now());
            subscriptionRepository.save(sub);
            log.info("Subscription canceled: id={}", stripeSub.getId());
        });
    }

    private void handlePaymentFailed(Event event) {
        Invoice invoice =
                (Invoice) event.getDataObjectDeserializer().getObject().orElseThrow();

        subscriptionRepository
                .findByStripeSubscriptionId(invoice.getSubscription())
                .ifPresent(sub -> {
                    sub.setStatus("past_due");
                    subscriptionRepository.save(sub);
                    log.warn("Payment failed for subscription={}", invoice.getSubscription());
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createStripeCustomer(UUID userId) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .putMetadata("userId", userId.toString())
                    .build();
            Customer customer = Customer.create(params);
            return customer.getId();
        } catch (StripeException e) {
            throw new BadRequestException("Failed to create Stripe customer: " + e.getMessage());
        }
    }

    /**
     * Maps a Stripe price ID to a plan name using configured price IDs.
     * Set STRIPE_PRICE_PRO / STRIPE_PRICE_PREMIUM / STRIPE_PRICE_FAMILY env vars.
     */
    private String resolvePlan(String priceId) {
        if (priceId == null) return "FREE";
        if (StringUtils.hasText(priceFamily) && priceId.equals(priceFamily)) return "FAMILY";
        if (StringUtils.hasText(pricePremium) && priceId.equals(pricePremium)) return "PREMIUM";
        if (StringUtils.hasText(pricePro) && priceId.equals(pricePro)) return "PRO";
        // Unknown Stripe price IDs should not silently map to a paid plan.
        return "FREE";
    }

    private void requireStripe() {
        if (!StringUtils.hasText(stripeSecretKey)) {
            throw new BadRequestException("Payment processing is not configured. Set STRIPE_SECRET_KEY.");
        }
    }

    private void validatePriceId(String priceId) {
        if (!StringUtils.hasText(priceId)) {
            throw new BadRequestException("Stripe priceId is required");
        }
        Set<String> allowed = java.util.stream.Stream.of(pricePro, pricePremium, priceFamily)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (allowed.isEmpty()) {
            throw new BadRequestException("Stripe price IDs are not configured");
        }
        if (!allowed.contains(priceId)) {
            throw new BadRequestException("Unsupported subscription plan");
        }
    }
}
