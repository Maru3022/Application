package com.healthlife.common.security;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {

    private UUID testUserId;
    private String testEmail;
    private String testToken;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@healthlife.com";
        testToken = "dummy-access-token-12345";
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_shouldReturnUserId_whenAuthenticated() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        testUserId, testEmail, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = SecurityUtils.getCurrentUserId();
        assertThat(result).isEqualTo(testUserId);
    }

    @Test
    void getCurrentUserId_shouldThrowUnauthorized_whenNotAuthenticated() {
        assertThatThrownBy(() -> SecurityUtils.getCurrentUserId())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class)
                .hasMessageContaining("User not authenticated");
    }

    @Test
    void getCurrentUserId_shouldThrowUnauthorized_whenPrincipalIsNotUUID() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "not-a-uuid", testEmail, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> SecurityUtils.getCurrentUserId())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class);
    }

    @Test
    void getCurrentUserEmail_shouldReturnEmail_whenAuthenticated() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        testUserId, testEmail, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = SecurityUtils.getCurrentUserEmail();
        assertThat(result).isEqualTo(testEmail);
    }

    @Test
    void getCurrentUserEmail_shouldThrowUnauthorized_whenNotAuthenticated() {
        assertThatThrownBy(() -> SecurityUtils.getCurrentUserEmail())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class);
    }

    @Test
    void getCurrentUserEmail_shouldThrowUnauthorized_whenCredentialsIsNotString() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        testUserId, 12345, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> SecurityUtils.getCurrentUserEmail())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class);
    }

    @Test
    void getCurrentUserAccessToken_shouldReturnToken_whenDetailsIsToken() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        testUserId, testEmail, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(testToken);
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = SecurityUtils.getCurrentUserAccessToken();
        assertThat(result).isEqualTo(testToken);
    }

    @Test
    void getCurrentUserAccessToken_shouldThrowUnauthorized_whenNoDetails() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        testUserId, testEmail, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> SecurityUtils.getCurrentUserAccessToken())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class);
    }

    @Test
    void getCurrentUserAccessToken_shouldThrowUnauthorized_whenNotAuthenticated() {
        assertThatThrownBy(() -> SecurityUtils.getCurrentUserAccessToken())
                .isInstanceOf(com.healthlife.common.exception.UnauthorizedException.class);
    }
}
