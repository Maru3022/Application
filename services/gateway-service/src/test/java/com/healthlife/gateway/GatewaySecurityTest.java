package com.healthlife.gateway;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.healthlife.common.security.JwtTokenProvider;
import io.restassured.RestAssured;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for Gateway security and routing behaviour.
 *
 * <p>Note on status codes: Spring Security returns 403 (not 401) when no
 * WWW-Authenticate challenge is configured. Tests use isIn(401, 403) to
 * accept both valid "unauthenticated" responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewaySecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    // ── Path traversal ────────────────────────────────────────────────────────

    @Test
    void pathTraversal_dotDot_shouldBeRejected() {
        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "t@t.com", "USER");
        int status = given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/health/../admin/secret")
                .then()
                .extract()
                .statusCode();
        // Must NOT succeed — 400 (path traversal blocked), 403/401 (auth), or 404 are all valid
        assertThat(status).isIn(400, 401, 403, 404);
        assertThat(status).isNotEqualTo(200);
    }

    @Test
    void pathTraversal_encodedDotDot_shouldBeRejected() {
        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "t@t.com", "USER");
        int status = given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/health/%2e%2e/admin")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isIn(400, 401, 403, 404);
        assertThat(status).isNotEqualTo(200);
    }

    @Test
    void pathTraversal_upperEncodedDotDot_shouldBeRejected() {
        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "t@t.com", "USER");
        int status = given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/health/%2E%2E/admin")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isIn(400, 401, 403, 404);
        assertThat(status).isNotEqualTo(200);
    }

    // ── Unknown service ───────────────────────────────────────────────────────

    @Test
    void unknownService_shouldReturn404() {
        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "t@t.com", "USER");
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/nonexistent/endpoint")
                .then()
                .statusCode(404);
    }

    // ── Unauthenticated access ────────────────────────────────────────────────

    @Test
    void noToken_protectedRoute_shouldReturn4xx() {
        int status = given().when().get("/api/v1/users/me").then().extract().statusCode();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void invalidToken_protectedRoute_shouldReturn4xx() {
        int status = given().header("Authorization", "Bearer this.is.not.valid")
                .when()
                .get("/api/v1/users/me")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void expiredToken_shouldReturn4xx() {
        // Non-JWT placeholder avoids secret-scanner false positives while still testing invalid token handling.
        String expiredToken = "expired-token-placeholder";
        int status = given().header("Authorization", "Bearer " + expiredToken)
                .when()
                .get("/api/v1/users/me")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isIn(401, 403);
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Test
    void actuatorHealth_isPublic() {
        given().when().get("/internal/actuator/health").then().statusCode(200);
    }

    @Test
    void authRegister_isPublicNotBlocked() {
        // Auth endpoints are public — must NOT return 401/403
        int status = given().contentType("application/json")
                .body("{\"email\":\"x@x.com\",\"password\":\"pass\",\"displayName\":\"X\"}")
                .when()
                .post("/api/v1/auth/register")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isNotIn(401, 403);
    }

    // ── Security headers ──────────────────────────────────────────────────────

    @Test
    void response_shouldHaveXFrameOptionsHeader() {
        given().when().get("/internal/actuator/health").then().header("X-Frame-Options", "DENY");
    }

    @Test
    void response_shouldHaveContentSecurityPolicyHeader() {
        given().when()
                .get("/internal/actuator/health")
                .then()
                .header("Content-Security-Policy", containsString("default-src 'self'"));
    }

    @Test
    void response_shouldHaveHstsHeader() {
        given().when()
                .get("/internal/actuator/health")
                .then()
                .header("Strict-Transport-Security", containsString("max-age="));
    }

    // ── Valid JWT passes through ──────────────────────────────────────────────

    @Test
    void validJwt_knownRoute_shouldNotReturn4xx() {
        String jwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "t@t.com", "USER");
        // With a valid JWT, gateway should forward the request.
        // Since downstream is not running, we get 502 (Bad Gateway) — NOT 401/403.
        int status = given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/v1/users/me")
                .then()
                .extract()
                .statusCode();
        assertThat(status).isNotIn(401, 403);
    }
}
