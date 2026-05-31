package com.healthlife.common.security;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for JwtTokenProvider covering:
 * - Access token generation and validation
 * - Refresh token generation and type check
 * - Claims extraction (userId, email, role)
 * - Invalid/expired/malformed token handling
 * - Short secret key rejection
 * - Blank secret rejection
 * - isRefreshToken distinguishes access vs refresh
 */
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "TestSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm2025TestTest";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(VALID_SECRET, 900_000L, 604_800_000L, "", "");
    }

    // ── constructor validation ────────────────────────────────────────────────

    @Test
    void constructor_blankSecret_shouldThrowIllegalState() {
        assertThatThrownBy(() -> new JwtTokenProvider("", 900_000L, 604_800_000L, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void constructor_nullSecret_shouldThrowIllegalState() {
        assertThatThrownBy(() -> new JwtTokenProvider(null, 900_000L, 604_800_000L, "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_shortSecret_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new JwtTokenProvider("tooshort", 900_000L, 604_800_000L, "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void constructor_exactly32CharSecret_shouldSucceed() {
        String secret32 = "12345678901234567890123456789012"; // exactly 32 chars
        assertThatCode(() -> new JwtTokenProvider(secret32, 900_000L, 604_800_000L, "", ""))
                .doesNotThrowAnyException();
    }

    // ── access token ──────────────────────────────────────────────────────────

    @Test
    void generateAccessToken_shouldBeValid() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "user@test.com", "USER");

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void generateAccessToken_shouldContainCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "user@test.com", "ADMIN");

        assertThat(provider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(provider.getEmailFromToken(token)).isEqualTo("user@test.com");
        assertThat(provider.getRoleFromToken(token)).isEqualTo("ADMIN");
    }

    @Test
    void generateAccessToken_isNotRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "user@test.com", "USER");

        assertThat(provider.isRefreshToken(token)).isFalse();
    }

    @Test
    void generateAccessToken_differentUsers_shouldHaveDifferentTokens() {
        String token1 = provider.generateAccessToken(UUID.randomUUID(), "a@test.com", "USER");
        String token2 = provider.generateAccessToken(UUID.randomUUID(), "b@test.com", "USER");

        assertThat(token1).isNotEqualTo(token2);
    }

    // ── refresh token ─────────────────────────────────────────────────────────

    @Test
    void generateRefreshToken_shouldBeValid() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateRefreshToken(userId);

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void generateRefreshToken_isRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateRefreshToken(userId);

        assertThat(provider.isRefreshToken(token)).isTrue();
    }

    @Test
    void generateRefreshToken_shouldContainUserId() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateRefreshToken(userId);

        assertThat(provider.getUserIdFromToken(token)).isEqualTo(userId);
    }

    // ── token validation ──────────────────────────────────────────────────────

    @Test
    void validateToken_invalidToken_shouldReturnFalse() {
        assertThat(provider.validateToken("not.a.valid.token")).isFalse();
    }

    @Test
    void validateToken_emptyString_shouldReturnFalse() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_nullLike_shouldReturnFalse() {
        assertThat(provider.validateToken("null")).isFalse();
    }

    @Test
    void validateToken_expiredToken_shouldReturnFalse() {
        // Create provider with 1ms expiration
        JwtTokenProvider shortLived = new JwtTokenProvider(VALID_SECRET, 1L, 604_800_000L, "", "");
        UUID userId = UUID.randomUUID();
        String token = shortLived.generateAccessToken(userId, "user@test.com", "USER");

        // Wait for expiration
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_tokenSignedWithDifferentKey_shouldReturnFalse() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "OtherSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm2025OtherOther",
                900_000L,
                604_800_000L,
                "",
                "");
        String token = otherProvider.generateAccessToken(UUID.randomUUID(), "x@x.com", "USER");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_truncatedToken_shouldReturnFalse() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "user@test.com", "USER");
        String truncated = token.substring(0, token.length() / 2);

        assertThat(provider.validateToken(truncated)).isFalse();
    }

    // ── isRefreshToken ────────────────────────────────────────────────────────

    @Test
    void isRefreshToken_withInvalidToken_shouldReturnFalse() {
        assertThat(provider.isRefreshToken("invalid.token.here")).isFalse();
    }

    @Test
    void isRefreshToken_withAccessToken_shouldReturnFalse() {
        String access = provider.generateAccessToken(UUID.randomUUID(), "x@x.com", "USER");
        assertThat(provider.isRefreshToken(access)).isFalse();
    }

    // ── expiration config ─────────────────────────────────────────────────────

    @Test
    void getAccessTokenExpirationMs_shouldReturnConfiguredValue() {
        assertThat(provider.getAccessTokenExpirationMs()).isEqualTo(900_000L);
    }

    @Test
    void getRefreshTokenExpirationMs_shouldReturnConfiguredValue() {
        assertThat(provider.getRefreshTokenExpirationMs()).isEqualTo(604_800_000L);
    }

    // ── issuer/audience validation ────────────────────────────────────────────

    @Test
    void tokenWithIssuer_shouldValidateCorrectly() {
        JwtTokenProvider withIssuer = new JwtTokenProvider(VALID_SECRET, 900_000L, 604_800_000L, "healthlife.com", "");
        UUID userId = UUID.randomUUID();
        String token = withIssuer.generateAccessToken(userId, "x@x.com", "USER");

        assertThat(withIssuer.validateToken(token)).isTrue();
        assertThat(withIssuer.getUserIdFromToken(token)).isEqualTo(userId);
    }
}
