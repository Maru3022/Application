package com.healthlife.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages FCM device tokens per user in Redis.
 *
 * <p>Key schema: {@code fcm:tokens:{userId}} → Redis Set of token strings. TTL is refreshed on
 * every registration to 90 days (tokens expire if the user hasn't opened the app).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final StringRedisTemplate redisTemplate;
    private static final long TOKEN_TTL_DAYS = 90;

    public void registerToken(UUID userId, String fcmToken) {
        String key = key(userId);
        redisTemplate.opsForSet().add(key, fcmToken);
        redisTemplate.expire(key, TOKEN_TTL_DAYS, TimeUnit.DAYS);
        log.info("FCM token registered for user={}", userId);
    }

    public void removeToken(UUID userId, String fcmToken) {
        redisTemplate.opsForSet().remove(key(userId), fcmToken);
        log.info("FCM token removed for user={}", userId);
    }

    public List<String> getTokensForUser(UUID userId) {
        var members = redisTemplate.opsForSet().members(key(userId));
        return members != null ? new ArrayList<>(members) : List.of();
    }

    private static String key(UUID userId) {
        return "fcm:tokens:" + userId;
    }
}
