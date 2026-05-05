package com.healthlife.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * FIX: Original code had a race condition — get→check→increment is not atomic. Two concurrent
 * requests could both read count=99, both pass the check, and both increment to 100.
 *
 * <p>Fix: Use Redis INCR (atomic) first, then check. If this is the first request, set TTL. This
 * pattern is safe under concurrent load.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private static final long MAX_REQUESTS = 100;
    private static final int WINDOW_SECONDS = 60;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = request.getRemoteAddr();
        String key = "rate_limit:" + clientId;

        // FIX: atomic increment first, then check — eliminates race condition
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            // Redis unavailable — fail open (allow request)
            filterChain.doFilter(request, response);
            return;
        }

        if (count == 1) {
            // First request in this window — set TTL atomically
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count > MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
