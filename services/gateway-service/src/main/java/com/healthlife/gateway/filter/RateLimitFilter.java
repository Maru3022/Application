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

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_REQUESTS = 100;
    private static final int WINDOW_SECONDS = 60;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientId = request.getRemoteAddr();
        String key = "rate_limit:" + clientId;

        String current = redisTemplate.opsForValue().get(key);
        int count = current != null ? Integer.parseInt(current) : 0;

        if (count >= MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        redisTemplate.opsForValue().increment(key);
        if (count == 0) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        filterChain.doFilter(request, response);
    }
}
