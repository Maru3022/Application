package com.healthlife.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthlife.auth.controller.AuthController;
import com.healthlife.auth.entity.User;
import com.healthlife.auth.repository.EmailVerificationTokenRepository;
import com.healthlife.auth.repository.PasswordResetTokenRepository;
import com.healthlife.auth.repository.RefreshTokenRepository;
import com.healthlife.auth.repository.UserRepository;
import com.healthlife.common.dto.auth.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the Auth service using Testcontainers to spin up a real PostgreSQL
 * database. These tests verify that JPA repositories, Flyway migrations, and the REST layer work
 * correctly together in an environment close to production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
@Tag("integration")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("healthlife_auth")
            .withUsername("healthlife")
            .withPassword("healthlife_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AuthController authController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

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
    void contextLoads() {
        assertThat(authController).isNotNull();
    }

    @Test
    void register_shouldPersistUserToPostgres() {
        RegisterRequest req = RegisterRequest.builder()
                .email("integration@health.com")
                .password("StrongPass123!")
                .displayName("Integration User")
                .build();

        var response = authController.register(req);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();

        User user = userRepository.findByEmail("integration@health.com").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("integration@health.com");
        assertThat(user.getDisplayName()).isEqualTo("Integration User");
    }

    @Test
    void register_duplicateEmail_shouldThrow() {
        RegisterRequest req = RegisterRequest.builder()
                .email("duplicate@health.com")
                .password("StrongPass123!")
                .displayName("Dup")
                .build();

        authController.register(req);

        assertThatThrownBy(() -> authController.register(req))
                .isInstanceOf(com.healthlife.common.exception.DuplicateResourceException.class);
    }
}
