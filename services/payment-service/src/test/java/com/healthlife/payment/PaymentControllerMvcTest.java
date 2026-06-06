package com.healthlife.payment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.payment.dto.*;
import com.healthlife.payment.service.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for PaymentController covering:
 * - GET /payments/subscription → 200
 * - POST /payments/checkout → 200 / 400 when Stripe not configured
 * - POST /payments/portal → 200 / 400
 * - POST /payments/webhook → 400 when no webhook secret
 * - 401 when no JWT
 */
@SpringBootTest(classes = PaymentServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    PaymentService paymentService;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    // ── GET /api/v1/payments/subscription ────────────────────────────────────

    @Test
    void getSubscription_shouldReturn200() throws Exception {
        when(paymentService.getSubscriptionStatus())
                .thenReturn(SubscriptionStatusResponse.builder()
                        .plan("FREE")
                        .status("active")
                        .build());

        mockMvc.perform(get("/api/v1/payments/subscription").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void getSubscription_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(get("/api/v1/payments/subscription")).andExpect(status().is4xxClientError());
    }

    // ── POST /api/v1/payments/checkout ────────────────────────────────────────

    @Test
    void createCheckout_stripeNotConfigured_shouldReturn400() throws Exception {
        when(paymentService.createCheckoutSession(anyString()))
                .thenThrow(new BadRequestException("STRIPE_SECRET_KEY not configured"));

        mockMvc.perform(post("/api/v1/payments/checkout?priceId=price_test123")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCheckout_missingPriceId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/checkout").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCheckout_validRequest_shouldReturn200() throws Exception {
        when(paymentService.createCheckoutSession("price_test123"))
                .thenReturn(CheckoutSessionResponse.builder()
                        .sessionId("cs_test_123")
                        .url("https://checkout.stripe.com/pay/cs_test_123")
                        .build());

        mockMvc.perform(post("/api/v1/payments/checkout?priceId=price_test123")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    // ── POST /api/v1/payments/portal ──────────────────────────────────────────

    @Test
    void createPortal_stripeNotConfigured_shouldReturn400() throws Exception {
        when(paymentService.createPortalSession())
                .thenThrow(new BadRequestException("STRIPE_SECRET_KEY not configured"));

        mockMvc.perform(post("/api/v1/payments/portal").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/payments/webhook ─────────────────────────────────────────

    @Test
    void webhook_noWebhookSecret_shouldReturn400() throws Exception {
        // webhook-secret is empty in test profile → controller returns 400
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_noSignatureHeader_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortal_validRequest_shouldReturn200() throws Exception {
        when(paymentService.createPortalSession())
                .thenReturn(CustomerPortalResponse.builder()
                        .url("https://billing.stripe.com/session/cs_test_123")
                        .build());

        mockMvc.perform(post("/api/v1/payments/portal").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void createPortal_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/payments/portal")).andExpect(status().is4xxClientError());
    }
}
