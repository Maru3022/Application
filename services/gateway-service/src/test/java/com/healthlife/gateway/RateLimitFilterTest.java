package com.healthlife.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.gateway.filter.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for RateLimitFilter covering:
 * - Per-IP rate limiting (unauthenticated)
 * - Per-user rate limiting (authenticated JWT)
 * - Redis unavailable → fail open
 * - First request sets TTL
 * - Limit exceeded returns 429 with Retry-After header
 * - Malformed JWT falls back to IP
 * - JWT with valid sub extracts userId correctly
 */
class RateLimitFilterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new RateLimitFilter(redisTemplate);
        chain = mock(FilterChain.class);
    }

    // ── Per-IP (unauthenticated) ──────────────────────────────────────────────

    @Test
    void unauthenticated_belowLimit_shouldPassThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void unauthenticated_atExactLimit_shouldPassThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(100L); // exactly at IP limit

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void unauthenticated_overLimit_shouldReturn429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(101L); // over IP limit of 100

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("Retry-After")).isEqualTo("60");
        assertThat(resp.getContentAsString()).contains("Rate limit exceeded");
    }

    // ── Per-user (authenticated JWT) ──────────────────────────────────────────

    @Test
    void authenticated_belowUserLimit_shouldPassThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(50L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.4");
        req.addHeader("Authorization", "Bearer " + buildFakeJwt("user-123"));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
        verify(valueOps).increment(argThat(k -> k.contains("user:user-123")));
    }

    @Test
    void authenticated_atUserLimit300_shouldPassThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(300L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + buildFakeJwt("user-456"));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void authenticated_overUserLimit_shouldReturn429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(301L); // over user limit of 300

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + buildFakeJwt("user-789"));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    // ── Redis unavailable ─────────────────────────────────────────────────────

    @Test
    void redisUnavailable_shouldFailOpen() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── TTL set on first request ──────────────────────────────────────────────

    @Test
    void firstRequest_shouldSetTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.6");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(redisTemplate).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void subsequentRequest_shouldNotResetTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(5L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    // ── Malformed JWT falls back to IP ────────────────────────────────────────

    @Test
    void malformedJwt_shouldFallBackToIp() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.8");
        req.addHeader("Authorization", "Bearer not.a.valid.jwt.at.all");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(chain).doFilter(req, resp);
        verify(valueOps).increment(argThat(k -> k.contains("ip:10.0.0.8")));
    }

    @Test
    void jwtWithoutSubClaim_shouldFallBackToIp() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        String payloadNoSub =
                java.util.Base64.getUrlEncoder().encodeToString("{\"email\":\"test@test.com\"}".getBytes());
        String fakeJwt = "header." + payloadNoSub + ".signature";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        req.addHeader("Authorization", "Bearer " + fakeJwt);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(valueOps).increment(argThat(k -> k.contains("ip:10.0.0.9")));
    }

    @Test
    void noAuthHeader_shouldUseIpKey() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        verify(valueOps).increment("rate_limit:ip:192.168.1.1");
    }

    // ── Response content type ─────────────────────────────────────────────────

    @Test
    void rateLimitExceeded_responseContentTypeIsJson() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(999L);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.10");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doFilter(req, resp);

        assertThat(resp.getContentType()).isEqualTo("application/json");
        assertThat(resp.getContentAsString()).contains("\"status\":429");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Calls the public doFilter method which internally calls doFilterInternal. */
    private void doFilter(MockHttpServletRequest req, MockHttpServletResponse resp)
            throws ServletException, IOException {
        filter.doFilter(req, resp, chain);
    }

    /**
     * Builds a fake (unsigned) JWT with the given subject for testing the sub extraction logic.
     */
    private static String buildFakeJwt(String sub) {
        String header =
                java.util.Base64.getUrlEncoder().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder()
                .encodeToString(("{\"sub\":\"" + sub + "\",\"email\":\"test@test.com\"}").getBytes());
        return header + "." + payload + ".fakesignature";
    }
}
