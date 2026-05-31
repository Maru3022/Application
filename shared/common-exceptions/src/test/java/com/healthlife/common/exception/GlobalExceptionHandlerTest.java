package com.healthlife.common.exception;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for GlobalExceptionHandler covering:
 * - Each exception type maps to correct HTTP status
 * - Error response body is populated
 * - Timestamp is set
 * - Message is resolved from MessageSource
 */
class GlobalExceptionHandlerTest {

    private MessageSource messageSource;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        // Return the message code as-is for simplicity
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2, String.class));
        handler = new GlobalExceptionHandler(messageSource);
    }

    // ── ResourceNotFoundException → 404 ──────────────────────────────────────

    @Test
    void handleNotFound_shouldReturn404() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleNotFound(new ResourceNotFoundException("User", "id", "123"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus()).isEqualTo(404);
        assertThat(resp.getBody().getTimestamp()).isNotNull();
    }

    // ── DuplicateResourceException → 409 ─────────────────────────────────────

    @Test
    void handleDuplicate_shouldReturn409() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleDuplicate(new DuplicateResourceException("Email already exists"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getStatus()).isEqualTo(409);
    }

    // ── UnauthorizedException → 401 ───────────────────────────────────────────

    @Test
    void handleUnauthorized_shouldReturn401() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleUnauthorized(new UnauthorizedException("Invalid credentials"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getStatus()).isEqualTo(401);
    }

    // ── ForbiddenException → 403 ──────────────────────────────────────────────

    @Test
    void handleForbidden_shouldReturn403() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleForbidden(new ForbiddenException("Access denied"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getStatus()).isEqualTo(403);
    }

    // ── BadRequestException → 400 ─────────────────────────────────────────────

    @Test
    void handleBadRequest_shouldReturn400() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleBadRequest(new BadRequestException("Invalid input"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getStatus()).isEqualTo(400);
    }

    // ── TokenRefreshException → 401 ───────────────────────────────────────────

    @Test
    void handleTokenRefresh_shouldReturn401() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleTokenRefresh(new TokenRefreshException("token123", "Token expired"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getStatus()).isEqualTo(401);
    }

    // ── Generic Exception → 500 ───────────────────────────────────────────────

    @Test
    void handleGeneric_shouldReturn500() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleGeneric(new RuntimeException("Unexpected error"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getStatus()).isEqualTo(500);
    }

    // ── error response structure ──────────────────────────────────────────────

    @Test
    void errorResponse_shouldHaveAllFields() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleNotFound(new ResourceNotFoundException("Item", "id", "42"));

        GlobalExceptionHandler.ErrorResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getStatus()).isPositive();
        assertThat(body.getError()).isNotBlank();
        assertThat(body.getMessage()).isNotBlank();
    }

    @Test
    void errorResponse_message_shouldBePopulated() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
                handler.handleBadRequest(new BadRequestException("Field is required"));

        assertThat(resp.getBody().getMessage()).isEqualTo("Field is required");
    }
}
