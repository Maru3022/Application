package com.healthlife.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.auth.entity.User;
import com.healthlife.auth.repository.EmailVerificationTokenRepository;
import com.healthlife.auth.repository.PasswordResetTokenRepository;
import com.healthlife.auth.repository.RefreshTokenRepository;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.auth.service.AuthService;
import com.healthlife.common.dto.auth.LoginRequest;
import com.healthlife.common.dto.auth.RegisterRequest;
import com.healthlife.common.exception.DuplicateResourceException;
import com.healthlife.common.exception.UnauthorizedException;
import com.healthlife.common.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class AuthServiceCriticalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    // Mock OAuthService to prevent google-api-client from triggering OAuth2 auto-configuration
    @MockitoBean
    private com.healthlife.auth.service.OAuthService oAuthService;

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

    @MockitoBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_shouldCreateUser() {
        RegisterRequest req = RegisterRequest.builder()
                .email("test@health.com")
                .password("StrongPass123!")
                .displayName("Test User")
                .build();

        var response = authService.register(req);
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_duplicateEmail_shouldThrow() {
        RegisterRequest req = RegisterRequest.builder()
                .email("dup@health.com")
                .password("StrongPass123!")
                .build();
        authService.register(req);

        assertThatThrownBy(() -> authService.register(req)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void register_shortPassword_shouldFailValidation() throws Exception {
        String body = """
            {"email":"test@health.com","password":"123","displayName":"Test"}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_shouldReturnTokens() {
        RegisterRequest reg = RegisterRequest.builder()
                .email("login@health.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);

        LoginRequest login = LoginRequest.builder()
                .email("login@health.com")
                .password("StrongPass123!")
                .build();

        var response = authService.login(login);
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_shouldThrow() {
        RegisterRequest reg = RegisterRequest.builder()
                .email("wrong@health.com")
                .password("StrongPass123!")
                .build();
        authService.register(reg);

        LoginRequest login = LoginRequest.builder()
                .email("wrong@health.com")
                .password("WrongPassword!")
                .build();

        assertThatThrownBy(() -> authService.login(login)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_nonExistentEmail_shouldThrow() {
        LoginRequest login = LoginRequest.builder()
                .email("nobody@health.com")
                .password("Whatever123!")
                .build();

        assertThatThrownBy(() -> authService.login(login)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshToken_valid_shouldReturnNewTokens() {
        RegisterRequest reg = RegisterRequest.builder()
                .email("refresh@health.com")
                .password("StrongPass123!")
                .build();
        var authResponse = authService.register(reg);

        var refreshReq = new com.healthlife.common.dto.auth.RefreshTokenRequest(authResponse.getRefreshToken());
        var newResponse = authService.refreshToken(refreshReq);
        assertThat(newResponse.getAccessToken()).isNotBlank();
    }

    @Test
    void refreshToken_invalid_shouldThrow() {
        var refreshReq = new com.healthlife.common.dto.auth.RefreshTokenRequest("invalid.token.here");
        assertThatThrownBy(() -> authService.refreshToken(refreshReq))
                .isInstanceOf(com.healthlife.common.exception.TokenRefreshException.class);
    }

    @Test
    void jwtToken_shouldContainCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, "test@test.com", "USER");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("test@test.com");
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("USER");
    }

    @Test
    void jwtToken_invalid_shouldReturnFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.token")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void passwordEncoder_shouldHashAndVerify() {
        String raw = "MySecurePassword123!";
        String encoded = passwordEncoder.encode(raw);
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(passwordEncoder.matches(raw, encoded)).isTrue();
        assertThat(passwordEncoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void login_deletedUser_shouldThrow() {
        User user = User.builder()
                .email("deleted@health.com")
                .passwordHash(passwordEncoder.encode("StrongPass123!"))
                .role("USER")
                .mfaEnabled(false)
                .build();
        user = userRepository.save(user);
        user.setDeletedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);

        LoginRequest login = LoginRequest.builder()
                .email("deleted@health.com")
                .password("StrongPass123!")
                .build();

        assertThatThrownBy(() -> authService.login(login)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void register_invalidEmail_shouldFailValidation() throws Exception {
        String body = """
            {"email":"not-an-email","password":"StrongPass123!"}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setupMfa_andVerifyEmail_andPasswordResetFlow_shouldWork() {
        RegisterRequest req = RegisterRequest.builder()
                .email("flow@health.com")
                .password("StrongPass123!")
                .displayName("Flow User")
                .build();
        var auth = authService.register(req);
        User user = userRepository.findByEmail("flow@health.com").orElseThrow();

        var mfa = authService.setupMfa(user.getId());
        assertThat(mfa.getSecret()).isNotBlank();
        assertThat(mfa.getQrCodeUri()).contains("otpauth://totp/HealthLife");

        assertThatThrownBy(() -> authService.verifyMfa(user.getId(), "abc123"))
                .isInstanceOf(com.healthlife.common.exception.BadRequestException.class);

        authService.requestPasswordReset(user.getEmail());
        var resetToken =
                passwordResetTokenRepository.findAll().stream().findFirst().orElseThrow();
        authService.confirmPasswordReset(resetToken.getToken(), "NewStrongPass123!");

        LoginRequest login = LoginRequest.builder()
                .email(user.getEmail())
                .password("NewStrongPass123!")
                .build();
        var loginResponse = authService.login(login);
        assertThat(loginResponse.getTokenType()).isEqualTo("MFA_REQUIRED");
        assertThat(refreshTokenRepository.findByToken(auth.getRefreshToken())).isEmpty();

        var verifyToken =
                emailVerificationTokenRepository.findAll().stream().findFirst().orElseThrow();
        authService.verifyEmail(verifyToken.getToken());
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getEmailVerified()).isTrue();
    }

    @Test
    void requestPasswordReset_nonExistingEmail_shouldBeNoOp() {
        authService.requestPasswordReset("no-such-user@health.com");
        assertThat(passwordResetTokenRepository.count()).isZero();
    }

    @Test
    void verifyMfaAndLogin_whenMfaDisabled_shouldThrow() {
        RegisterRequest req = RegisterRequest.builder()
                .email("mfa-disabled@health.com")
                .password("StrongPass123!")
                .displayName("No Mfa")
                .build();
        authService.register(req);
        assertThatThrownBy(() -> authService.verifyMfaAndLogin("mfa-disabled@health.com", "123456"))
                .isInstanceOf(com.healthlife.common.exception.BadRequestException.class);
    }

    @Test
    void oauthEndpoints_shouldAcceptBodyPayload() throws Exception {
        com.healthlife.common.dto.auth.AuthResponse resp = com.healthlife.common.dto.auth.AuthResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .tokenType("Bearer")
                .expiresIn(900000)
                .build();
        when(oAuthService.loginWithGoogle(any())).thenReturn(resp);
        when(oAuthService.loginWithApple(any(), any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));

        mockMvc.perform(
                        post("/api/v1/auth/oauth/apple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identityToken\":\"apple-token\",\"email\":\"apple@user.com\",\"fullName\":\"Apple User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}
