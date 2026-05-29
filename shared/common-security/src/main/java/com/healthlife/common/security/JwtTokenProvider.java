package com.healthlife.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final String issuer;
    private final String audience;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token.expiration:900000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token.expiration:604800000}") long refreshTokenExpirationMs,
            @Value("${jwt.issuer:}") String issuer,
            @Value("${jwt.audience:}") String audience) {
        // Проверяем длину ключа — HS256 требует минимум 256 бит (32 байта)
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " + "Generate a secure key: openssl rand -base64 64");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters (256 bits) for HS256. "
                    + "Current length: " + secret.length() + " chars");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.issuer = issuer == null ? "" : issuer.trim();
        this.audience = audience == null ? "" : audience.trim();
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs));
        if (!issuer.isEmpty()) {
            builder.issuer(issuer);
        }
        if (!audience.isEmpty()) {
            builder.claim("aud", audience);
        }
        return builder.signWith(key).compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                .signWith(key)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired");
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token unsupported");
        } catch (MalformedJwtException e) {
            log.debug("JWT token malformed");
        } catch (JwtException e) {
            log.debug("JWT token invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is empty or null");
        }
        return false;
    }

    public boolean isRefreshToken(String token) {
        // FIX: original code threw if token was invalid — now guarded
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    // FIX: centralised parse so we don't repeat parser setup in every method
    private Claims parseClaims(String token) {
        Claims claims =
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        validateIssuerAndAudience(claims);
        return claims;
    }

    /** Проверка iss/aud при наличии конфигурации (production). */
    private void validateIssuerAndAudience(Claims claims) {
        if (!issuer.isEmpty() && !issuer.equals(claims.getIssuer())) {
            throw new JwtException("Invalid JWT issuer");
        }
        if (!audience.isEmpty()) {
            Object aud = claims.get("aud");
            boolean valid = false;
            if (aud instanceof String audStr) {
                valid = audience.equals(audStr);
            } else if (aud instanceof java.util.List<?> audList) {
                valid = audList.stream().anyMatch(audience::equals);
            }
            if (!valid) {
                throw new JwtException("Invalid JWT audience");
            }
        }
    }
}
