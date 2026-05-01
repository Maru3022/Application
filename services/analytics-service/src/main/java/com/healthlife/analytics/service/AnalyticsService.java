package com.healthlife.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StringRedisTemplate redisTemplate;

    public void trackEvent(UUID userId, String eventName, String properties) {
        String key = "analytics:event:" + userId + ":" + eventName;
        redisTemplate.opsForValue().set(key, properties, 30, TimeUnit.DAYS);
        log.info("Event tracked: user={}, event={}", userId, eventName);
    }

    public String getEvent(UUID userId, String eventName) {
        String key = "analytics:event:" + userId + ":" + eventName;
        return redisTemplate.opsForValue().get(key);
    }
}
