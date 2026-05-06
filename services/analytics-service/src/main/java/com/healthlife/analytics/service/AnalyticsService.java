package com.healthlife.analytics.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Lightweight analytics service backed by Redis.
 *
 * <p>Events are stored as a Redis List (RPUSH) so that multiple occurrences of the same
 * (userId, eventName) pair are preserved. Each entry is a JSON-like string:
 * {@code <timestamp>|<properties>}. The list TTL is reset to 30 days on every write.
 *
 * <p>This replaces the previous implementation that used SET and silently overwrote every
 * previous event for the same key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final long TTL_DAYS = 30;
    /** Maximum number of events kept per (userId, eventName) pair to bound memory usage. */
    private static final long MAX_EVENTS_PER_KEY = 1000;

    private final StringRedisTemplate redisTemplate;

    public void trackEvent(UUID userId, String eventName, String properties) {
        String key = buildKey(userId, eventName);
        // Append timestamped entry to the list
        String entry = Instant.now().toEpochMilli() + "|" + (properties != null ? properties : "{}");
        redisTemplate.opsForList().rightPush(key, entry);
        // Trim to the most recent MAX_EVENTS_PER_KEY entries to prevent unbounded growth
        redisTemplate.opsForList().trim(key, -MAX_EVENTS_PER_KEY, -1);
        // Refresh TTL on every write
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
        log.info("Event tracked: user={}, event={}", userId, eventName);
    }

    /**
     * Returns all stored occurrences of the given event for the user, most-recent last.
     * Returns an empty list if no events have been recorded.
     */
    public List<String> getEvents(UUID userId, String eventName) {
        String key = buildKey(userId, eventName);
        List<String> events = redisTemplate.opsForList().range(key, 0, -1);
        return events != null ? events : new ArrayList<>();
    }

    /**
     * Returns the most recent occurrence of the given event, or {@code null} if none exists.
     *
     * @deprecated Prefer {@link #getEvents(UUID, String)} to avoid losing historical data.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public String getEvent(UUID userId, String eventName) {
        List<String> events = getEvents(userId, eventName);
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    private static String buildKey(UUID userId, String eventName) {
        return "analytics:events:" + userId + ":" + eventName;
    }
}
