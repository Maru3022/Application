package com.healthlife.payment.controller;

import com.healthlife.payment.dto.*;
import com.healthlife.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    /**
     * Creates a Stripe Checkout Session.
     *
     * @param priceId Stripe price ID (e.g. price_xxx) — obtain from Stripe Dashboard
     * @return session URL to redirect the user to Stripe's hosted checkout page
     */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutSessionResponse> createCheckout(@RequestParam @NotBlank String priceId) {
        return ResponseEntity.ok(paymentService.createCheckoutSession(priceId));
    }

    /**
     * Creates a Stripe Customer Portal session for subscription management
     * (update payment method, cancel, view invoices).
     */
    @PostMapping("/portal")
    public ResponseEntity<CustomerPortalResponse> createPortal() {
        return ResponseEntity.ok(paymentService.createPortalSession());
    }

    /**
     * Returns the current subscription status for the authenticated user.
     */
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionStatusResponse> getSubscription() {
        return ResponseEntity.ok(paymentService.getSubscriptionStatus());
    }

    /**
     * Stripe webhook endpoint. Stripe sends events here when subscription state changes.
     *
     * <p>The endpoint verifies the Stripe-Signature header to ensure the request is genuine.
     * Configure the webhook URL in Stripe Dashboard → Webhooks → Add endpoint:
     * {@code https://api.healthlife.com/api/v1/payments/webhook}
     *
     * <p>Required events: checkout.session.completed, customer.subscription.updated,
     * customer.subscription.deleted, invoice.payment_failed
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {

        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("STRIPE_WEBHOOK_SECRET not configured — rejecting webhook");
            return ResponseEntity.badRequest().build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        paymentService.handleWebhook(event);
        return ResponseEntity.ok().build();
    }
}
