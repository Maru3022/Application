package com.healthlife.auth.service;

import com.healthlife.auth.entity.EmailVerificationToken;
import com.healthlife.auth.entity.PasswordResetToken;
import com.healthlife.auth.entity.RefreshToken;
import com.healthlife.auth.entity.User;
import com.healthlife.auth.repository.EmailVerificationTokenRepository;
import com.healthlife.auth.repository.PasswordResetTokenRepository;
import com.healthlife.auth.repository.RefreshTokenRepository;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.common.dto.auth.*;
import com.healthlife.common.exception.*;
import com.healthlife.common.security.JwtTokenProvider;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации.
 *
 * <p>Micrometer-счётчики инициализируются в конструкторе (не внутри методов) —
 * это позволяет избежать повторной регистрации одного и того же счётчика в метрическом реестре
 * при каждом вызове метода.
 *
 * <p>GoogleAuthenticator инжектируется как Spring-бин (определён в {@code AuthConfig}) —
 * это обеспечивает тестируемость: в unit-тестах можно подменить бин через мок.
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final MeterRegistry meterRegistry;
    private final GoogleAuthenticator googleAuthenticator;

    // Счётчики инициализируются один раз в конструкторе
    private final Counter registerAttemptCounter;
    private final Counter registerSuccessCounter;
    private final Counter registerFailureCounter;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            EmailService emailService,
            MeterRegistry meterRegistry,
            GoogleAuthenticator googleAuthenticator) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.meterRegistry = meterRegistry;
        this.googleAuthenticator = googleAuthenticator;

        this.registerAttemptCounter = Counter.builder("auth.register.attempts")
                .tag("service", "auth-service")
                .register(meterRegistry);
        this.registerSuccessCounter = Counter.builder("auth.register.success")
                .register(meterRegistry);
        this.registerFailureCounter = Counter.builder("auth.register.failures")
                .tag("reason", "duplicate_email")
                .register(meterRegistry);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());
        registerAttemptCounter.increment();

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already registered: {}", request.getEmail());
            registerFailureCounter.increment();
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .timezone("UTC")
                .emailVerified(false)
                .mfaEnabled(false)
                .role("USER")
                .build();
        user = userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(24))
                .used(false)
                .build();
        emailVerificationTokenRepository.save(emailToken);
        emailService.sendEmailVerificationEmail(user.getEmail(), verificationToken);

        log.info("User registered successfully with ID: {}", user.getId());
        registerSuccessCounter.increment();
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for email: {}", request.getEmail());
        Timer.Sample sample = Timer.start(meterRegistry);
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> {
            log.warn("Login failed: Invalid email: {}", request.getEmail());
            return new UnauthorizedException("Invalid email or password");
        });

        if (user.getDeletedAt() != null) {
            log.warn("Login failed: Account deleted for email: {}", request.getEmail());
            throw new UnauthorizedException("Account has been deleted");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: Invalid password for email: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getMfaEnabled()) {
            log.info("MFA required for user: {}", user.getId());
            return AuthResponse.builder()
                    .accessToken("")
                    .refreshToken("")
                    .tokenType("MFA_REQUIRED")
                    .expiresIn(0)
                    .build();
        }

        log.info("Login successful for user: {}", user.getId());
        sample.stop(Timer.builder("auth.login.duration").tag("outcome", "success").register(meterRegistry));
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();
        log.info("Attempting to refresh token");

        if (!jwtTokenProvider.validateToken(refreshTokenStr) || !jwtTokenProvider.isRefreshToken(refreshTokenStr)) {
            log.warn("Invalid refresh token");
            throw new TokenRefreshException(refreshTokenStr, "Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenStr)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found: {}", refreshTokenStr);
                    return new TokenRefreshException(refreshTokenStr, "Token not found");
                });

        if (refreshToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            log.warn("Refresh token expired: {}", refreshTokenStr);
            throw new TokenRefreshException(refreshTokenStr, "Token expired");
        }

        User user = refreshToken.getUser();
        log.info("Token refreshed for user: {}", user.getId());
        return generateAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        log.info("Logging out with refresh token");
        refreshTokenRepository.findByToken(refreshToken).ifPresent(rt -> {
            refreshTokenRepository.delete(rt);
            log.info("Refresh token deleted for logout");
        });
    }

    @Transactional
    public MfaSetupResponse setupMfa(UUID userId) {
        log.info("Setting up MFA for user: {}", userId);
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getMfaEnabled()) {
            log.warn("MFA already enabled for user: {}", userId);
            throw new BadRequestException("MFA is already enabled");
        }

        // Используем инжектированный бин, а не new GoogleAuthenticator()
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();

        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);
        log.info("MFA setup completed for user: {}", userId);

        String qrCodeUri =
                String.format("otpauth://totp/HealthLife:%s?secret=%s&issuer=HealthLife", user.getEmail(), secret);

        return MfaSetupResponse.builder().secret(secret).qrCodeUri(qrCodeUri).build();
    }

    public boolean verifyMfa(UUID userId, String code) {
        log.debug("Verifying MFA for user: {}", userId);
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
            log.warn("MFA not enabled for user: {}", userId);
            throw new BadRequestException("MFA is not enabled for this user");
        }

        boolean valid = googleAuthenticator.authorize(user.getMfaSecret(), parseTotpCode(code));
        log.info("MFA verification {} for user: {}", valid ? "successful" : "failed", userId);
        return valid;
    }

    @Transactional
    public AuthResponse verifyMfaAndLogin(String email, String code) {
        log.info("Verifying MFA and logging in for email: {}", email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            log.warn("MFA login failed: Invalid email: {}", email);
            return new UnauthorizedException("Invalid credentials");
        });

        if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
            log.warn("MFA not enabled for email: {}", email);
            throw new BadRequestException("MFA is not enabled");
        }

        if (!googleAuthenticator.authorize(user.getMfaSecret(), parseTotpCode(code))) {
            log.warn("Invalid MFA code for email: {}", email);
            throw new UnauthorizedException("Invalid MFA code");
        }

        log.info("MFA login successful for user: {}", user.getId());
        return generateAuthResponse(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Requesting password reset for email: {}", email);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("Password reset requested for non-existent email (silently ignored)");
            return;
        }
        User user = userOpt.get();

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(1))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
        log.info("Password reset token generated for user: {}", user.getId());
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        log.info("Confirming password reset with token");
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.getUsed() || resetToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            log.warn("Password reset token is used or expired");
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.deleteByUserId(user.getId());

        log.info("Password reset confirmed for user: {}", user.getId());
    }

    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token");
        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token"));

        if (verificationToken.getUsed() || verificationToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            log.warn("Email verification token is used or expired");
            throw new BadRequestException("Invalid or expired verification token");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified for user: {}", user.getId());
    }

    AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken entity = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs() / 1000))
                .build();
        refreshTokenRepository.save(entity);

        log.debug("Auth response generated for user: {}", user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .build();
    }

    AuthResponse generateAuthResponsePublic(User user) {
        return generateAuthResponse(user);
    }

    private int parseTotpCode(String code) {
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid TOTP code format");
            throw new BadRequestException("MFA code must be a 6-digit number");
        }
    }
}
