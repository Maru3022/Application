package com.healthlife.gateway;

import static io.restassured.RestAssured.given;

import com.healthlife.common.security.JwtTokenProvider;
import io.restassured.RestAssured;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Lightweight contract tests that verify the Gateway exposes the expected routes and returns
 * sensible status codes when downstream services are not available. These tests ensure that route
 * configuration changes do not accidentally break the public API surface.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String validJwt;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        validJwt = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "test@healthlife.com", "USER");
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
        given().when().get("/actuator/health").then().statusCode(200);
    }

    @Test
    void gatewaySwagger_shouldBeAccessible() {
        given().when().get("/swagger-ui.html").then().statusCode(200);
    }
}
