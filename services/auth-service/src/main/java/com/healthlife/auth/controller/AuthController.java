package com.healthlife.auth.controller;

import com.healthlife.auth.service.AuthService;
import com.healthlife.auth.service.OAuthService;
import com.healthlife.common.dto.auth.*;
import com.healthlife.common.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;

    @PostMapping("/register")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(authService.setupMfa(userId));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        // FIX: email must come from the request body, not a client-controlled header,
        // to prevent header injection / identity spoofing during MFA verification.
        return ResponseEntity.ok(authService.verifyMfaAndLogin(request.getEmail(), request.getCode()));
    }

    @PostMapping("/password/reset")
    @RateLimiter(name = "login")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password/reset/confirm")
    @RateLimiter(name = "login")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verify-email/{token}")
    public ResponseEntity<Void> verifyEmail(@PathVariable String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    /**
     * Google Sign-In — verifies a Google ID token from the mobile client and returns
     * HealthLife JWT tokens. Creates a new account on first sign-in.
     */
    @PostMapping("/oauth/google")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody OAuth2Request request) {
        return ResponseEntity.ok(oAuthService.loginWithGoogle(request.getIdToken()));
    }

    /**
     * Apple Sign-In — verifies an Apple identity token and returns HealthLife JWT tokens.
     * {@code email} and {@code fullName} are only provided by Apple on the first sign-in.
     */
    @PostMapping("/oauth/apple")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> loginWithApple(@Valid @RequestBody AppleOAuthRequest request) {
        return ResponseEntity.ok(
                oAuthService.loginWithApple(request.getIdentityToken(), request.getEmail(), request.getFullName()));
    }
}
