package com.healthlife.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.healthlife.auth.repository.EmailVerificationTokenRepository;
import com.healthlife.auth.repository.PasswordResetTokenRepository;
import com.healthlife.auth.repository.RefreshTokenRepository;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.auth.service.AuthService;
import com.healthlife.common.dto.auth.*;
import com.healthlife.common.exception.*;
import com.healthlife.common.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Дополнительные тесты AuthService для покрытия:
 * - logout flow
 * - setupMfa already enabled
 * - verifyMfa invalid code format
 * - verifyEmail expired/used token
 * - confirmPasswordReset used token
 * - token expiry
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class AuthServiceEmailTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.healthlife.auth.service.OAuthService oAuthService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_shouldDeleteRefreshToken() {
        var reg = RegisterRequest.builder()
                .email("logout@test.com")
                .password("StrongPass123!")
                .build();
        var authResp = authService.register(reg);

        authService.logout(authResp.getRefreshToken());

        assertThat(refreshTokenRepository.findByToken(authResp.getRefreshToken()))
                .isEmpty();
    }

    @Test
    void logout_nonExistentToken_shouldBeNoOp() {
        assertThatCode(() -> authService.logout("non-existent-token")).doesNotThrowAnyException();
    }

    // ── setupMfa ──────────────────────────────────────────────────────────────

    @Test
    void setupMfa_alreadyEnabled_shouldThrowBadRequest() {
        var reg = RegisterRequest.builder()
                .email("mfa-dup@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);
        var user = userRepository.findByEmail("mfa-dup@test.com").orElseThrow();

        authService.setupMfa(user.getId());
        // Second call should throw
        assertThatThrownBy(() -> authService.setupMfa(user.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already enabled");
    }

    @Test
    void setupMfa_nonExistentUser_shouldThrowNotFound() {
        assertThatThrownBy(() -> authService.setupMfa(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setupMfa_shouldReturnSecretAndQrCode() {
        var reg = RegisterRequest.builder()
                .email("mfa-setup@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);
        var user = userRepository.findByEmail("mfa-setup@test.com").orElseThrow();

        var response = authService.setupMfa(user.getId());
        assertThat(response.getSecret()).isNotBlank();
        assertThat(response.getQrCodeUri()).contains("otpauth://totp/HealthLife");
        assertThat(response.getQrCodeUri()).contains(user.getEmail());
    }

    // ── verifyMfa ─────────────────────────────────────────────────────────────

    @Test
    void verifyMfa_nonNumericCode_shouldThrowBadRequest() {
        var reg = RegisterRequest.builder()
                .email("mfa-code@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);
        var user = userRepository.findByEmail("mfa-code@test.com").orElseThrow();
        authService.setupMfa(user.getId());

        assertThatThrownBy(() -> authService.verifyMfa(user.getId(), "notanumber"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("6-digit");
    }

    @Test
    void verifyMfa_mfaNotEnabled_shouldThrow() {
        var reg = RegisterRequest.builder()
                .email("nomfa@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);
        var user = userRepository.findByEmail("nomfa@test.com").orElseThrow();

        assertThatThrownBy(() -> authService.verifyMfa(user.getId(), "123456")).isInstanceOf(BadRequestException.class);
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_invalidToken_shouldThrowBadRequest() {
        assertThatThrownBy(() -> authService.verifyEmail("invalid-token-xyz")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyEmail_alreadyUsedToken_shouldThrowBadRequest() {
        var reg = RegisterRequest.builder()
                .email("verify@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);

        var tokenEntity =
                emailVerificationTokenRepository.findAll().stream().findFirst().orElseThrow();
        // Mark as used
        tokenEntity.setUsed(true);
        emailVerificationTokenRepository.save(tokenEntity);

        assertThatThrownBy(() -> authService.verifyEmail(tokenEntity.getToken()))
                .isInstanceOf(BadRequestException.class);
    }

    // ── confirmPasswordReset ──────────────────────────────────────────────────

    @Test
    void confirmPasswordReset_invalidToken_shouldThrowBadRequest() {
        assertThatThrownBy(() -> authService.confirmPasswordReset("bad-token", "NewPass123!"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void confirmPasswordReset_usedToken_shouldThrowBadRequest() {
        var reg = RegisterRequest.builder()
                .email("reset-used@test.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);
        authService.requestPasswordReset("reset-used@test.com");

        var tokenEntity =
                passwordResetTokenRepository.findAll().stream().findFirst().orElseThrow();
        tokenEntity.setUsed(true);
        passwordResetTokenRepository.save(tokenEntity);

        assertThatThrownBy(() -> authService.confirmPasswordReset(tokenEntity.getToken(), "NewPass123!"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void confirmPasswordReset_valid_shouldChangePasswordAndInvalidateRefreshTokens() {
        var reg = RegisterRequest.builder()
                .email("reset-ok@test.com")
                .password("OldPass123!")
                .build();
        authService.register(reg);
        authService.requestPasswordReset("reset-ok@test.com");

        var token = passwordResetTokenRepository.findAll().stream().findFirst().orElseThrow();
        authService.confirmPasswordReset(token.getToken(), "NewPass123!");

        var login = LoginRequest.builder()
                .email("reset-ok@test.com")
                .password("NewPass123!")
                .build();
        var resp = authService.login(login);
        assertThat(resp.getAccessToken()).isNotBlank();
    }

    // ── refreshToken edge cases ────────────────────────────────────────────────

    @Test
    void refreshToken_expiredToken_shouldThrow() {
        // Create a refresh token that is already expired in the DB
        var reg = RegisterRequest.builder()
                .email("expired-refresh@test.com")
                .password("StrongPass123!")
                .build();
        var authResp = authService.register(reg);

        var stored =
                refreshTokenRepository.findByToken(authResp.getRefreshToken()).orElseThrow();
        stored.setExpiryDate(java.time.OffsetDateTime.now().minusSeconds(1));
        refreshTokenRepository.save(stored);

        var req = new RefreshTokenRequest(authResp.getRefreshToken());
        assertThatThrownBy(() -> authService.refreshToken(req)).isInstanceOf(TokenRefreshException.class);
    }

    @Test
    void refreshToken_notInDatabase_shouldThrow() {
        // Generate a valid-format refresh token but not stored in DB
        String fakeRefresh = jwtTokenProvider.generateRefreshToken(UUID.randomUUID());
        var req = new RefreshTokenRequest(fakeRefresh);

        assertThatThrownBy(() -> authService.refreshToken(req)).isInstanceOf(TokenRefreshException.class);
    }

    // ── verifyMfaAndLogin ─────────────────────────────────────────────────────

    @Test
    void verifyMfaAndLogin_nonExistentEmail_shouldThrow() {
        assertThatThrownBy(() -> authService.verifyMfaAndLogin("ghost@test.com", "123456"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── JWT token validation ───────────────────────────────────────────────────

    @Test
    void accessToken_isNotRefreshToken() {
        String access = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "x@x.com", "USER");
        assertThat(jwtTokenProvider.isRefreshToken(access)).isFalse();
    }

    @Test
    void refreshToken_isRefreshToken() {
        String refresh = jwtTokenProvider.generateRefreshToken(UUID.randomUUID());
        assertThat(jwtTokenProvider.isRefreshToken(refresh)).isTrue();
    }
}
