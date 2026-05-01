package com.healthlife.gateway.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Simple reverse-proxy gateway that routes external requests to internal microservices. Each
 * downstream call is protected by Resilience4j circuit breaker, retry, and rate limiter
 * annotations. Fallback methods return RFC 7807 {@link ProblemDetail} responses when a service is
 * unavailable.
 */
@Component
@RestController
public class GatewayRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Map<String, String> ROUTES = Map.of(
            "/api/v1/auth", "http://localhost:8081",
            "/api/v1/users", "http://localhost:8082",
            "/api/v1/health", "http://localhost:8083",
            "/api/v1/mental", "http://localhost:8084",
            "/api/v1/nutrition", "http://localhost:8085",
            "/api/v1/ai", "http://localhost:8086",
            "/api/v1/social", "http://localhost:8087",
            "/api/v1/notifications", "http://localhost:8088",
            "/api/v1/analytics", "http://localhost:8089");

    @RequestMapping("/api/v1/{service}/**")
    @CircuitBreaker(name = "gateway", fallbackMethod = "proxyFallback")
    @Retry(name = "gateway")
    @RateLimiter(name = "gateway")
    public ResponseEntity<?> proxyRequest(
            @PathVariable String service,
            HttpServletRequest request,
            @RequestBody(required = false) String body,
            @RequestHeader HttpHeaders headers,
            HttpMethod method) {

        String prefix = "/api/v1/" + service;
        String targetBase = ROUTES.get(prefix);
        if (targetBase == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "No route registered for service: " + service);
            problem.setTitle("Service Not Found");
            problem.setType(URI.create("https://healthlife.com/errors/service-not-found"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }

        String path = request.getRequestURI();
        String targetUrl = targetBase + path + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        HttpHeaders forwardedHeaders = new HttpHeaders();
        forwardedHeaders.putAll(headers);
        forwardedHeaders.remove("host");
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null) {
            forwardedHeaders.set("X-Request-Id", requestId);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, forwardedHeaders);
        return restTemplate.exchange(targetUrl, method, entity, String.class);
    }

    @SuppressWarnings("unused")
    private ResponseEntity<?> proxyFallback(
            String service,
            HttpServletRequest request,
            String body,
            HttpHeaders headers,
            HttpMethod method,
            Throwable t) {
        log.warn(
                "Gateway fallback triggered for service={} due to {}",
                service,
                t.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Service " + service + " is currently unavailable. Please retry later.");
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("https://healthlife.com/errors/service-unavailable"));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    /** Internal exception used to transport {@link ProblemDetail} through the filter chain. */
    public static class GatewayRouteException extends RuntimeException {
        private final ProblemDetail problemDetail;

        public GatewayRouteException(ProblemDetail problemDetail) {
            this.problemDetail = problemDetail;
        }

        public ProblemDetail getProblemDetail() {
            return problemDetail;
        }
    }
}
