package com.healthlife.mental.support.service;

import com.healthlife.common.exception.BadRequestException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Лимит запросов в чат поддержки: N сообщений в минуту на пользователя. */
@Component
public class SupportRateLimiter {

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile int requestsPerMinute = 20;

    public void configure(int requestsPerMinute) {
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        buckets.clear();
    }

    public void checkLimit(UUID userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, id -> newBucket());
        if (!bucket.tryConsume(1)) {
            throw new BadRequestException("support.rate.limit");
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
