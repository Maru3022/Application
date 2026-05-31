package com.healthlife.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.analytics.service.AnalyticsService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Unit tests for AnalyticsService covering:
 * - trackEvent stores entry and sets TTL
 * - getEvents returns stored events
 * - getEventCount returns correct count
 * - getUserSummary aggregates all events
 * - getEvent (deprecated) returns last event
 * - Redis null responses handled gracefully
 * - MAX_EVENTS_PER_KEY trim is called
 */
class AnalyticsServiceTest {

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOps;
    private AnalyticsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        service = new AnalyticsService(redisTemplate);
    }

    // ── trackEvent ────────────────────────────────────────────────────────────

    @Test
    void trackEvent_shouldPushToRedis() {
        UUID userId = UUID.randomUUID();
        service.trackEvent(userId, "step_goal_reached", "{\"steps\":10000}");

        verify(listOps).rightPush(eq("analytics:events:" + userId + ":step_goal_reached"), anyString());
    }

    @Test
    void trackEvent_shouldSetTtl() {
        UUID userId = UUID.randomUUID();
        service.trackEvent(userId, "login", null);

        verify(redisTemplate)
                .expire(eq("analytics:events:" + userId + ":login"), eq(30L), eq(java.util.concurrent.TimeUnit.DAYS));
    }

    @Test
    void trackEvent_shouldTrimToMaxEvents() {
        UUID userId = UUID.randomUUID();
        service.trackEvent(userId, "water_logged", "{}");

        verify(listOps).trim(eq("analytics:events:" + userId + ":water_logged"), eq(-1000L), eq(-1L));
    }

    @Test
    void trackEvent_nullProperties_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.trackEvent(userId, "event", null)).doesNotThrowAnyException();
    }

    @Test
    void trackEvent_entryContainsTimestamp() {
        UUID userId = UUID.randomUUID();
        long before = System.currentTimeMillis();

        service.trackEvent(userId, "mood_logged", "{\"score\":8}");

        verify(listOps).rightPush(anyString(), argThat(entry -> {
            String[] parts = entry.split("\\|");
            if (parts.length < 2) return false;
            long ts = Long.parseLong(parts[0]);
            return ts >= before && ts <= System.currentTimeMillis();
        }));
    }

    // ── getEvents ─────────────────────────────────────────────────────────────

    @Test
    void getEvents_shouldReturnStoredEvents() {
        UUID userId = UUID.randomUUID();
        List<String> stored = List.of("1000|{}", "2000|{}");
        when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(stored);

        List<String> result = service.getEvents(userId, "login");

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(stored);
    }

    @Test
    void getEvents_redisReturnsNull_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);

        List<String> result = service.getEvents(userId, "login");

        assertThat(result).isEmpty();
    }

    @Test
    void getEvents_noEvents_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        List<String> result = service.getEvents(userId, "nonexistent");

        assertThat(result).isEmpty();
    }

    // ── getEventCount ─────────────────────────────────────────────────────────

    @Test
    void getEventCount_shouldReturnSize() {
        UUID userId = UUID.randomUUID();
        when(listOps.size(anyString())).thenReturn(42L);

        long count = service.getEventCount(userId, "step_goal_reached");

        assertThat(count).isEqualTo(42L);
    }

    @Test
    void getEventCount_redisReturnsNull_shouldReturnZero() {
        UUID userId = UUID.randomUUID();
        when(listOps.size(anyString())).thenReturn(null);

        long count = service.getEventCount(userId, "event");

        assertThat(count).isZero();
    }

    // ── getUserSummary ────────────────────────────────────────────────────────

    @Test
    void getUserSummary_shouldAggregateAllEvents() {
        UUID userId = UUID.randomUUID();
        String prefix = "analytics:events:" + userId + ":";
        Set<String> keys = Set.of(prefix + "login", prefix + "mood_logged");
        when(redisTemplate.keys(prefix + "*")).thenReturn(keys);
        when(listOps.size(prefix + "login")).thenReturn(5L);
        when(listOps.size(prefix + "mood_logged")).thenReturn(12L);

        Map<String, Long> summary = service.getUserSummary(userId);

        assertThat(summary).containsEntry("login", 5L);
        assertThat(summary).containsEntry("mood_logged", 12L);
    }

    @Test
    void getUserSummary_noKeys_shouldReturnEmptyMap() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.keys(anyString())).thenReturn(null);

        Map<String, Long> summary = service.getUserSummary(userId);

        assertThat(summary).isEmpty();
    }

    @Test
    void getUserSummary_nullSizeForKey_shouldDefaultToZero() {
        UUID userId = UUID.randomUUID();
        String key = "analytics:events:" + userId + ":login";
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(listOps.size(key)).thenReturn(null);

        Map<String, Long> summary = service.getUserSummary(userId);

        assertThat(summary).containsEntry("login", 0L);
    }

    // ── getEvent (deprecated) ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void getEvent_shouldReturnLastEvent() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of("1000|{}", "2000|{\"score\":9}"));

        String result = service.getEvent(userId, "mood_logged");

        assertThat(result).isEqualTo("2000|{\"score\":9}");
    }

    @Test
    @SuppressWarnings("deprecation")
    void getEvent_noEvents_shouldReturnNull() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        String result = service.getEvent(userId, "nonexistent");

        assertThat(result).isNull();
    }

    // ── key format ────────────────────────────────────────────────────────────

    @Test
    void trackEvent_keyFormatIsCorrect() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        service.trackEvent(userId, "test_event", "{}");

        String expectedKey = "analytics:events:00000000-0000-0000-0000-000000000001:test_event";
        verify(listOps).rightPush(eq(expectedKey), anyString());
    }
}
