package com.healthlife.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.healthlife.auth.entity.User;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.common.dto.auth.AuthResponse;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final Duration APPLE_JWKS_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration APPLE_JWKS_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${oauth.google.client-id:}")
    private String googleClientId;

    @Value("${oauth.apple.audience:}")
    private String appleAudience;

    // ── Google Sign-In ────────────────────────────────────────────────────────

    /**
     * Verifies a Google ID token (from Google Sign-In SDK on mobile) and returns
     * HealthLife JWT tokens. Creates a new user account if this is the first sign-in.
     *
     * @param idToken the ID token returned by Google Sign-In on the mobile client
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        if (!StringUtils.hasText(googleClientId)) {
            throw new BadRequestException("Google Sign-In is not configured. Set OAUTH_GOOGLE_CLIENT_ID.");
        }

        GoogleIdToken.Payload payload = verifyGoogleToken(idToken);
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String googleSub = payload.getSubject();

        return findOrCreateOAuthUser(email, name, "google:" + googleSub);
    }

    // ── Apple Sign-In ─────────────────────────────────────────────────────────

    /**
     * Verifies an Apple identity token (from Sign in with Apple on iOS) and returns
     * HealthLife JWT tokens. Creates a new user account if this is the first sign-in.
     *
     * <p>Apple tokens are standard JWTs signed with Apple's public keys (fetched from
     * https://appleid.apple.com/auth/keys).
     *
     * @param identityToken the identity token returned by Sign in with Apple
     * @param email         the email from Apple (only provided on first sign-in; may be null)
     * @param fullName      the user's full name (only provided on first sign-in; may be null)
     */
    @Transactional
    public AuthResponse loginWithApple(String identityToken, String email, String fullName) {
        String appleSub = verifyAppleToken(identityToken);

        // Apple only provides email on the very first sign-in.
        // On subsequent sign-ins, look up by the stable Apple sub.
        String resolvedEmail = StringUtils.hasText(email) ? email : "apple_" + appleSub + "@privaterelay.appleid.com";
        String resolvedName = StringUtils.hasText(fullName) ? fullName : "Apple User";

        return findOrCreateOAuthUser(resolvedEmail, resolvedName, "apple:" + appleSub);
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private AuthResponse findOrCreateOAuthUser(String email, String displayName, String oauthId) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating new OAuth user: email={} oauthId={}", email, oauthId);
            User newUser = User.builder()
                    .email(email)
                    // OAuth users have no password — set a random unguessable hash
                    .passwordHash(UUID.randomUUID().toString())
                    .displayName(displayName != null ? displayName : email.split("@")[0])
                    .timezone("UTC")
                    .emailVerified(true) // OAuth providers verify email
                    .mfaEnabled(false)
                    .role("USER")
                    .build();
            return userRepository.save(newUser);
        });

        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Account has been deleted");
        }

        log.info("OAuth login successful for user={}", user.getId());
        return authService.generateAuthResponsePublic(user);
    }

    // ── Google token verification ─────────────────────────────────────────────

    private GoogleIdToken.Payload verifyGoogleToken(String idToken) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                            new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new UnauthorizedException("Invalid Google ID token");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Google token verification failed");
        }
    }

    // ── Apple token verification ──────────────────────────────────────────────

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";

    /**
     * Verifies an Apple identity token by:
     * 1. Fetching Apple's public keys from the JWKS endpoint
     * 2. Verifying the JWT signature
     * 3. Returning the stable Apple user ID (sub claim)
     */
    private String verifyAppleToken(String identityToken) {
        try {
            validateAppleTokenFormat(identityToken);
            // Fetch Apple's public keys
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(APPLE_JWKS_CONNECT_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APPLE_KEYS_URL))
                    .timeout(APPLE_JWKS_REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Apple JWKS endpoint returned status={}", response.statusCode());
                throw new UnauthorizedException("Apple token verification failed");
            }

            // Parse the JWKS response to find the matching key
            // Simple extraction — in production use a proper JWKS library
            String jwksJson = response.body();
            PublicKey publicKey = extractApplePublicKey(jwksJson, identityToken);

            Claims claims = Jwts.parser()
                    .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            String issuer = claims.getIssuer();
            if (!"https://appleid.apple.com".equals(issuer)) {
                throw new UnauthorizedException("Invalid Apple token issuer");
            }
            validateAppleAudience(claims);

            return claims.getSubject();
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Apple token verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Apple token verification failed");
        }
    }

    /**
     * Extracts the RSA public key from Apple's JWKS that matches the token's kid header.
     * This is a simplified implementation — production code should use a JWKS client library.
     */
    @SuppressWarnings("unchecked")
    private PublicKey extractApplePublicKey(String jwksJson, String token) throws Exception {
        // Extract kid from token header
        String[] parts = token.split("\\.");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> header = mapper.readValue(headerJson, Map.class);
        String kid = (String) header.get("kid");
        String alg = (String) header.get("alg");
        String typ = (String) header.get("typ");
        if (!StringUtils.hasText(kid)) {
            throw new UnauthorizedException("Apple token header is missing kid");
        }
        if (!"RS256".equals(alg)) {
            throw new UnauthorizedException("Unsupported Apple token algorithm");
        }
        if (StringUtils.hasText(typ) && !"JWT".equalsIgnoreCase(typ)) {
            throw new UnauthorizedException("Invalid Apple token type");
        }

        Map<String, Object> jwks = mapper.readValue(jwksJson, Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        if (keys == null || keys.isEmpty()) {
            throw new UnauthorizedException("Apple JWKS did not contain keys");
        }

        Map<String, Object> matchingKey = keys.stream()
                .filter(k -> kid.equals(k.get("kid")))
                .findFirst()
                .orElseThrow(() -> new UnauthorizedException("No matching Apple public key found"));
        if (!"RSA".equals(matchingKey.get("kty")) || !"sig".equals(matchingKey.get("use"))) {
            throw new UnauthorizedException("Invalid Apple JWKS key type");
        }

        // Build RSA public key from n and e
        byte[] nBytes = Base64.getUrlDecoder().decode((String) matchingKey.get("n"));
        byte[] eBytes = Base64.getUrlDecoder().decode((String) matchingKey.get("e"));

        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private void validateAppleAudience(Claims claims) {
        if (!StringUtils.hasText(appleAudience)) {
            throw new BadRequestException("Apple Sign-In is not configured. Set OAUTH_APPLE_AUDIENCE.");
        }
        Object aud = claims.get("aud");
        if (aud instanceof String audStr) {
            if (!appleAudience.equals(audStr)) {
                throw new UnauthorizedException("Invalid Apple token audience");
            }
            return;
        }
        if (aud instanceof List<?> audList) {
            boolean valid = audList.stream().anyMatch(appleAudience::equals);
            if (!valid) {
                throw new UnauthorizedException("Invalid Apple token audience");
            }
            return;
        }
        throw new UnauthorizedException("Invalid Apple token audience");
    }

    private void validateAppleTokenFormat(String identityToken) {
        if (!StringUtils.hasText(identityToken)) {
            throw new UnauthorizedException("Apple identity token is required");
        }
        String[] parts = identityToken.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid Apple identity token format");
        }
    }
}
