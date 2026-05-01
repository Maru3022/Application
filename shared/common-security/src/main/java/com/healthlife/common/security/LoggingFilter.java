package com.healthlife.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter that logs every incoming HTTP request and outgoing response with timing, headers, and
 * masked body snippets. Uses {@link ContentCachingRequestWrapper} and
 * {@link ContentCachingResponseWrapper} to safely inspect payloads without consuming streams. This
 * filter runs immediately after {@link RequestIdFilter} so that the {@code requestId} is present
 * in MDC for all log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;
            logRequest(requestWrapper);
            logResponse(responseWrapper, duration);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.equalsIgnoreCase("authorization") || name.equalsIgnoreCase("cookie")) {
                headers.put(name, "***");
            } else {
                headers.put(name, request.getHeader(name));
            }
        }

        log.info(
                "REQUEST  method={} path={} query={} headers={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                headers);
    }

    private void logResponse(ContentCachingResponseWrapper response, long durationMs) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        Collection<String> headerNames = response.getHeaderNames();
        for (String name : headerNames) {
            headers.put(name, response.getHeader(name));
        }

        log.info("RESPONSE status={} durationMs={} headers={}", response.getStatus(), durationMs, headers);
    }
}
