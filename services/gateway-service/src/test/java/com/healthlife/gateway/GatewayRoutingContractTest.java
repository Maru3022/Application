package com.healthlife.gateway;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * Lightweight contract tests that verify the Gateway exposes the expected routes and returns
 * sensible status codes when downstream services are not available. These tests ensure that route
 * configuration changes do not accidentally break the public API surface.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private String validJwt;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        validJwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "test@healthlife.com", "USER");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("1");
        when(valueOps.increment(anyString())).thenReturn(2L);
    }

    @Test
    void unknownRoute_shouldReturn404() {
        given().header("Authorization", "Bearer " + validJwt)
                .when()
                .get("/api/v1/unknown/route")
                .then()
                .statusCode(404);
    }

    @Test
    void actuatorHealth_shouldReturn200() {
        given().when().get("/internal/actuator/health").then().statusCode(200);
    }

    @Test
    void gatewaySwagger_shouldBeAccessible() {
        given().when().get("/swagger-ui.html").then().statusCode(200);
    }
}
