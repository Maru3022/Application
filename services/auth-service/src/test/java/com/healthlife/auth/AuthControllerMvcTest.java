package com.healthlife.auth;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.auth.service.AuthService;
import com.healthlife.auth.service.OAuthService;
import com.healthlife.common.dto.auth.*;
import com.healthlife.common.exception.DuplicateResourceException;
import com.healthlife.common.exception.UnauthorizedException;
import com.healthlife.common.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc controller tests for AuthController covering HTTP layer:
 * - 200 on valid register/login
 * - 400 on validation failures
 * - 409 on duplicate email
 * - 401 on wrong credentials
 * - Public endpoints accessible without JWT
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class AuthControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    OAuthService oAuthService;

    @MockitoBean
    JavaMailSender javaMailSender;

    private static final AuthResponse MOCK_AUTH = AuthResponse.builder()
            .accessToken("access.token.here")
            .refreshToken("refresh.token.here")
            .tokenType("Bearer")
            .expiresIn(900000L)
            .build();

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    void register_validRequest_shouldReturn200() throws Exception {
        when(authService.register(any())).thenReturn(MOCK_AUTH);

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"new@test.com","password":"StrongPass123!","displayName":"New User"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_missingEmail_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"password":"StrongPass123!"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"not-an-email","password":"StrongPass123!"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"test@test.com","password":"123"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_shouldReturn409() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateResourceException("Email already registered"));

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"dup@test.com","password":"StrongPass123!"}
                            """))
                .andExpect(status().isConflict());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    void login_validCredentials_shouldReturn200() throws Exception {
        when(authService.login(any())).thenReturn(MOCK_AUTH);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"user@test.com","password":"StrongPass123!"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void login_wrongPassword_shouldReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"user@test.com","password":"WrongPass!"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_missingPassword_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"user@test.com"}
                            """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Test
    void refresh_validToken_shouldReturn200() throws Exception {
        when(authService.refreshToken(any())).thenReturn(MOCK_AUTH);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"refreshToken":"valid.refresh.token"}
                            """))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    void logout_shouldReturn200() throws Exception {
        doNothing().when(authService).logout(anyString());

        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Refresh-Token", "some.refresh.token"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/auth/verify-email/{token} ─────────────────────────────────

    @Test
    void verifyEmail_shouldReturn200() throws Exception {
        doNothing().when(authService).verifyEmail(anyString());

        mockMvc.perform(get("/api/v1/auth/verify-email/some-token-123")).andExpect(status().isOk());
    }

    // ── POST /api/v1/auth/password/reset ─────────────────────────────────────

    @Test
    void requestPasswordReset_shouldReturn200() throws Exception {
        doNothing().when(authService).requestPasswordReset(anyString());

        mockMvc.perform(
                        post("/api/v1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"user@test.com"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void requestPasswordReset_invalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"not-an-email"}
                            """))
                .andExpect(status().isBadRequest());
    }

    // ── Security: public endpoints accessible without JWT ─────────────────────

    @Test
    void authEndpoints_arePublic_noJwtRequired() throws Exception {
        when(authService.register(any())).thenReturn(MOCK_AUTH);

        // Should NOT return 401/403
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"email":"pub@test.com","password":"StrongPass123!"}
                            """))
                .andExpect(status().isOk());
    }
}
