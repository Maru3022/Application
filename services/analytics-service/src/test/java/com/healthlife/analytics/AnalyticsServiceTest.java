package com.healthlife.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.analytics.service.AnalyticsService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Unit tests for AnalyticsService covering:
 * - trackEvent stores timestamped entry in Redis List
 * - trackEvent trims list to MAX_EVENTS_PER_KEY
 * - trackEvent refreshes TTL on every write
 * - trackEvent with null properties stores "{}"
 * - getEvents returns all entries
 * - getEvents returns empty list when no events
 * - getEvents handles null from Redis
 * - getEvent (deprecated) returns last entry
 * - getEvent returns null when no events
 * - Key format is correct
 * - Different users have different keys
 * - Different event names have different keys
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
    void trackEvent_shouldRightPushToList() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "page_view", "{\"page\":\"dashboard\"}");

        verify(listOps).rightPush(eq("analytics:events:" + userId + ":page_view"), anyString());
    }

    @Test
    void trackEvent_entryContainsTimestampAndProperties() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "button_click", "{\"button\":\"add_water\"}");

        verify(listOps)
                .rightPush(
                        anyString(),
                        argThat(entry -> entry.contains("|") && entry.contains("{\"button\":\"add_water\"}")));
    }

    @Test
    void trackEvent_nullProperties_shouldStoreEmptyJson() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "app_open", null);

        verify(listOps).rightPush(anyString(), argThat(entry -> entry.endsWith("|{}")));
    }

    @Test
    void trackEvent_shouldTrimListTo1000() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "scroll", "{}");

        verify(listOps).trim(anyString(), eq(-1000L), eq(-1L));
    }

    @Test
    void trackEvent_shouldRefreshTtl30Days() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "login", "{}");

        verify(redisTemplate).expire(anyString(), eq(30L), eq(TimeUnit.DAYS));
    }

    @Test
    void trackEvent_multipleEvents_eachStoredSeparately() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "event_a", "{}");
        service.trackEvent(userId, "event_b", "{}");
        service.trackEvent(userId, "event_a", "{}"); // same event again

        verify(listOps, times(2)).rightPush(eq("analytics:events:" + userId + ":event_a"), anyString());
        verify(listOps, times(1)).rightPush(eq("analytics:events:" + userId + ":event_b"), anyString());
    }

    // ── getEvents ─────────────────────────────────────────────────────────────

    @Test
    void getEvents_shouldReturnAllEntries() {
        UUID userId = UUID.randomUUID();
        List<String> stored = List.of("1000000000000|{\"a\":1}", "1000000000001|{\"a\":2}", "1000000000002|{\"a\":3}");
        when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(stored);

        List<String> result = service.getEvents(userId, "my_event");

        assertThat(result).hasSize(3).isEqualTo(stored);
    }

    @Test
    void getEvents_noEvents_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        List<String> result = service.getEvents(userId, "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void getEvents_redisReturnsNull_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);

        List<String> result = service.getEvents(userId, "event");

        assertThat(result).isEmpty();
    }

    // ── getEvent (deprecated) ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void getEvent_shouldReturnLastEntry() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of("100|first", "200|second", "300|last"));

        String result = service.getEvent(userId, "event");

        assertThat(result).isEqualTo("300|last");
    }

    @Test
    @SuppressWarnings("deprecation")
    void getEvent_noEvents_shouldReturnNull() {
        UUID userId = UUID.randomUUID();
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        String result = service.getEvent(userId, "event");

        assertThat(result).isNull();
    }

    // ── Key format ────────────────────────────────────────────────────────────

    @Test
    void keyFormat_shouldBeCorrect() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        service.trackEvent(userId, "test_event", "{}");

        verify(listOps).rightPush(eq("analytics:events:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:test_event"), anyString());
    }

    @Test
    void differentUsers_shouldHaveDifferentKeys() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        service.trackEvent(user1, "login", "{}");
        service.trackEvent(user2, "login", "{}");

        verify(listOps).rightPush(eq("analytics:events:" + user1 + ":login"), anyString());
        verify(listOps).rightPush(eq("analytics:events:" + user2 + ":login"), anyString());
    }

    @Test
    void differentEventNames_shouldHaveDifferentKeys() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "event_a", "{}");
        service.trackEvent(userId, "event_b", "{}");

        verify(listOps).rightPush(eq("analytics:events:" + userId + ":event_a"), anyString());
        verify(listOps).rightPush(eq("analytics:events:" + userId + ":event_b"), anyString());
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void trackEvent_emptyProperties_shouldStore() {
        UUID userId = UUID.randomUUID();

        service.trackEvent(userId, "event", "");

        verify(listOps).rightPush(anyString(), anyString());
    }

    @Test
    void trackEvent_specialCharsInEventName_shouldNotCrash() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> service.trackEvent(userId, "event:with:colons", "{}"))
                .doesNotThrowAnyException();
    }
}
