package com.healthlife.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.notification.service.DeviceTokenService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Unit tests for DeviceTokenService covering:
 * - Register token stores in Redis Set with TTL
 * - Remove token deletes from Redis Set
 * - getTokensForUser returns all tokens
 * - getTokensForUser returns empty list when no tokens
 * - getTokensForUser handles null from Redis gracefully
 * - Multiple tokens per user
 * - Key format is correct
 */
class DeviceTokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private SetOperations<String, String> setOps;
    private DeviceTokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new DeviceTokenService(redisTemplate);
    }

    // ── registerToken ─────────────────────────────────────────────────────────

    @Test
    void registerToken_shouldAddToRedisSet() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-abc123";

        service.registerToken(userId, token);

        verify(setOps).add("fcm:tokens:" + userId, token);
    }

    @Test
    void registerToken_shouldSetTtl90Days() {
        UUID userId = UUID.randomUUID();

        service.registerToken(userId, "token-xyz");

        verify(redisTemplate).expire("fcm:tokens:" + userId, 90L, TimeUnit.DAYS);
    }

    @Test
    void registerToken_multipleTokens_shouldAddEach() {
        UUID userId = UUID.randomUUID();

        service.registerToken(userId, "token-1");
        service.registerToken(userId, "token-2");
        service.registerToken(userId, "token-3");

        verify(setOps).add("fcm:tokens:" + userId, "token-1");
        verify(setOps).add("fcm:tokens:" + userId, "token-2");
        verify(setOps).add("fcm:tokens:" + userId, "token-3");
    }

    // ── removeToken ───────────────────────────────────────────────────────────

    @Test
    void removeToken_shouldRemoveFromRedisSet() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-to-remove";

        service.removeToken(userId, token);

        verify(setOps).remove("fcm:tokens:" + userId, token);
    }

    // ── getTokensForUser ──────────────────────────────────────────────────────

    @Test
    void getTokensForUser_shouldReturnAllTokens() {
        UUID userId = UUID.randomUUID();
        when(setOps.members("fcm:tokens:" + userId)).thenReturn(Set.of("token-a", "token-b", "token-c"));

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).hasSize(3).containsExactlyInAnyOrder("token-a", "token-b", "token-c");
    }

    @Test
    void getTokensForUser_noTokens_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(setOps.members(anyString())).thenReturn(Set.of());

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).isEmpty();
    }

    @Test
    void getTokensForUser_redisReturnsNull_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(setOps.members(anyString())).thenReturn(null);

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).isEmpty();
    }

    // ── Key format ────────────────────────────────────────────────────────────

    @Test
    void keyFormat_shouldBeCorrect() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        service.registerToken(userId, "token");

        verify(setOps).add("fcm:tokens:11111111-1111-1111-1111-111111111111", "token");
    }

    @Test
    void differentUsers_shouldHaveDifferentKeys() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        service.registerToken(user1, "token-1");
        service.registerToken(user2, "token-2");

        verify(setOps).add("fcm:tokens:" + user1, "token-1");
        verify(setOps).add("fcm:tokens:" + user2, "token-2");
        // Keys must be different
        assertThat("fcm:tokens:" + user1).isNotEqualTo("fcm:tokens:" + user2);
    }
}
