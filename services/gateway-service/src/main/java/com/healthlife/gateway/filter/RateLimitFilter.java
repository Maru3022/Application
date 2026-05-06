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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two-tier rate limiter:
 *
 * <ol>
 *   <li><b>Per-user</b> (authenticated): 300 req / 60 s — identified by JWT subject extracted from
 *       the {@code Authorization} header. This prevents a single user behind a shared NAT from
 *       consuming the entire IP quota.
 *   <li><b>Per-IP</b> (fallback / unauthenticated): 100 req / 60 s — applied when no valid Bearer
 *       token is present (login, register, public endpoints).
 * </ol>
 *
 * <p>Both tiers use atomic Redis INCR to avoid the get→check→increment race condition.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    private static final long USER_MAX_REQUESTS = 300;
    private static final long IP_MAX_REQUESTS = 100;
    private static final int WINDOW_SECONDS = 60;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = resolveClientId(request);
        String key = "rate_limit:" + clientId;
        long limit = clientId.startsWith("user:") ? USER_MAX_REQUESTS : IP_MAX_REQUESTS;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            // Redis unavailable — fail open to avoid blocking all traffic
            filterChain.doFilter(request, response);
            return;
        }

        if (count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.getWriter()
                    .write("{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Rate limit exceeded. Please retry after "
                            + WINDOW_SECONDS
                            + " seconds.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts a stable client identifier. Prefers the JWT subject (userId) so that per-user
     * limits apply regardless of IP. Falls back to remote IP for unauthenticated requests.
     */
    private String resolveClientId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Extract subject from JWT payload (middle segment) without full validation —
            // full validation happens in JwtAuthenticationFilter. We only need the subject
            // for rate-limit bucketing; an invalid token falls back to IP.
            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    // Extract "sub" field — simple string search avoids a full JSON parse
                    int subIdx = payload.indexOf("\"sub\":\"");
                    if (subIdx >= 0) {
                        int start = subIdx + 7;
                        int end = payload.indexOf('"', start);
                        if (end > start) {
                            return "user:" + payload.substring(start, end);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Malformed token — fall through to IP-based limiting
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
