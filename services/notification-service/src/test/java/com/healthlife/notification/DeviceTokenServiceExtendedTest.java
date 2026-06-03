package com.healthlife.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.notification.service.DeviceTokenService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Тесты DeviceTokenService:
 * - registerToken calls SADD + expire
 * - removeToken calls SREM
 * - getTokensForUser returns tokens
 * - getTokensForUser returns empty on null Redis response
 * - registerToken refreshes TTL each time
 * - key format is correct
 */
class DeviceTokenServiceExtendedTest {

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

    @Test
    void registerToken_shouldCallSaddAndExpire() {
        UUID userId = UUID.randomUUID();
        service.registerToken(userId, "fcm_token_abc");

        verify(setOps).add("fcm:tokens:" + userId, "fcm_token_abc");
        verify(redisTemplate).expire("fcm:tokens:" + userId, 90L, TimeUnit.DAYS);
    }

    @Test
    void registerToken_shouldRefreshTtlEachTime() {
        UUID userId = UUID.randomUUID();
        service.registerToken(userId, "token1");
        service.registerToken(userId, "token2");

        verify(redisTemplate, times(2))
                .expire("fcm:tokens:" + userId, 90L, TimeUnit.DAYS);
    }

    @Test
    void removeToken_shouldCallSrem() {
        UUID userId = UUID.randomUUID();
        service.removeToken(userId, "fcm_token_abc");

        verify(setOps).remove("fcm:tokens:" + userId, "fcm_token_abc");
    }

    @Test
    void getTokensForUser_shouldReturnTokens() {
        UUID userId = UUID.randomUUID();
        when(setOps.members("fcm:tokens:" + userId))
                .thenReturn(Set.of("token_a", "token_b", "token_c"));

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).hasSize(3);
        assertThat(tokens).containsExactlyInAnyOrder("token_a", "token_b", "token_c");
    }

    @Test
    void getTokensForUser_redisReturnsNull_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(setOps.members(anyString())).thenReturn(null);

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).isEmpty();
    }

    @Test
    void getTokensForUser_emptySet_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(setOps.members(anyString())).thenReturn(Set.of());

        List<String> tokens = service.getTokensForUser(userId);

        assertThat(tokens).isEmpty();
    }

    @Test
    void keyFormat_shouldIncludeUserId() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        service.registerToken(userId, "token");

        verify(setOps).add("fcm:tokens:00000000-0000-0000-0000-000000000001", "token");
    }

    @Test
    void registerToken_multipleTokens_allRegistered() {
        UUID userId = UUID.randomUUID();
        service.registerToken(userId, "token1");
        service.registerToken(userId, "token2");
        service.registerToken(userId, "token3");

        verify(setOps, times(3)).add(eq("fcm:tokens:" + userId), anyString());
    }

    @Test
    void removeToken_shouldNotThrow_whenTokenNotPresent() {
        UUID userId = UUID.randomUUID();
        when(setOps.remove(anyString(), any())).thenReturn(0L);

        assertThatCode(() -> service.removeToken(userId, "nonexistent_token"))
                .doesNotThrowAnyException();
    }
}
