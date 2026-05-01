package com.healthlife.auth.service;

import com.healthlife.auth.entity.PasswordResetToken;
import com.healthlife.auth.entity.RefreshToken;
import com.healthlife.auth.entity.User;
import com.healthlife.auth.repository.RefreshTokenRepository;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.common.dto.auth.*;
import com.healthlife.common.exception.*;
import com.healthlife.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
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

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Account has been deleted");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getMfaEnabled()) {
            return AuthResponse.builder()
                    .accessToken("")
                    .refreshToken("")
                    .tokenType("MFA_REQUIRED")
                    .expiresIn(0)
                    .build();
        }

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshTokenStr) || !jwtTokenProvider.isRefreshToken(refreshTokenStr)) {
            throw new TokenRefreshException(refreshTokenStr, "Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenRefreshException(refreshTokenStr, "Token not found"));

        if (refreshToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException(refreshTokenStr, "Token expired");
        }

        User user = refreshToken.getUser();
        return generateAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public MfaSetupResponse setupMfa(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled");
        }

        com.warrenstrange.googleauth.GoogleAuthenticator gAuth = new com.warrenstrange.googleauth.GoogleAuthenticator();
        com.warrenstrange.googleauth.GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);

        String qrCodeUri = String.format(
                "otpauth://totp/HealthLife:%s?secret=%s&issuer=HealthLife",
                user.getEmail(), secret
        );

        return MfaSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrCodeUri)
                .build();
    }

    public boolean verifyMfa(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
            throw new BadRequestException("MFA is not enabled for this user");
        }

        com.warrenstrange.googleauth.GoogleAuthenticator gAuth = new com.warrenstrange.googleauth.GoogleAuthenticator();
        return gAuth.authorize(user.getMfaSecret(), Integer.parseInt(code));
    }

    @Transactional
    public AuthResponse verifyMfaAndLogin(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
            throw new BadRequestException("MFA is not enabled");
        }

        com.warrenstrange.googleauth.GoogleAuthenticator gAuth = new com.warrenstrange.googleauth.GoogleAuthenticator();
        if (!gAuth.authorize(user.getMfaSecret(), Integer.parseInt(code))) {
            throw new UnauthorizedException("Invalid MFA code");
        }

        return generateAuthResponse(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusHours(1))
                .used(false)
                .build();
        // In production, send email with reset link
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        // Implementation for password reset confirmation
    }

    @Transactional
    public void verifyEmail(String token) {
        // Implementation for email verification
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken entity = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiryDate(OffsetDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs() / 1000))
                .build();
        refreshTokenRepository.save(entity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .build();
    }
}
