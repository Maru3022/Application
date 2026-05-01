package com.healthlife.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds security-related HTTP response headers to every outgoing gateway response. These headers
 * help mitigate common web vulnerabilities (XSS, clickjacking, MIME-sniffing) and enforce HTTPS
 * via HSTS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none';");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        filterChain.doFilter(request, response);
    }
}
