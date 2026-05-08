package com.healthlife.gateway.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Simple reverse-proxy gateway that routes external requests to internal microservices. Each
 * downstream call is protected by Resilience4j circuit breaker, retry, and rate limiter. Fallback
 * methods return RFC 7807 ProblemDetail responses when a service is unavailable.
 *
 * <p>FIX: Routes configurable via env vars (default = localhost for local dev, overridden to K8s
 * service names via Helm). FIX: RestTemplate configured with connection/read timeouts.
 */
@Component
@RestController
public class GatewayRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

    private final RestTemplate restTemplate;

    private final Map<String, String> routes;

    public GatewayRouteConfig(
            @Value("${gateway.routes.auth:http://localhost:8081}") String authUrl,
            @Value("${gateway.routes.users:http://localhost:8082}") String usersUrl,
            @Value("${gateway.routes.health:http://localhost:8083}") String healthUrl,
            @Value("${gateway.routes.mental:http://localhost:8084}") String mentalUrl,
            @Value("${gateway.routes.nutrition:http://localhost:8085}") String nutritionUrl,
            @Value("${gateway.routes.ai:http://localhost:8086}") String aiUrl,
            @Value("${gateway.routes.social:http://localhost:8087}") String socialUrl,
            @Value("${gateway.routes.notifications:http://localhost:8088}") String notificationsUrl,
            @Value("${gateway.routes.analytics:http://localhost:8089}") String analyticsUrl,
            @Value("${gateway.routes.payments:http://localhost:8090}") String paymentsUrl,
            RestTemplateBuilder restTemplateBuilder) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();

        this.routes = new LinkedHashMap<>();
        this.routes.put("/api/v1/auth", authUrl);
        this.routes.put("/api/v1/users", usersUrl);
        this.routes.put("/api/v1/health", healthUrl);
        this.routes.put("/api/v1/mental", mentalUrl);
        this.routes.put("/api/v1/nutrition", nutritionUrl);
        this.routes.put("/api/v1/ai", aiUrl);
        this.routes.put("/api/v1/social", socialUrl);
        this.routes.put("/api/v1/notifications", notificationsUrl);
        this.routes.put("/api/v1/analytics", analyticsUrl);
        this.routes.put("/api/v1/payments", paymentsUrl);
    }

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
        String targetBase = routes.get(prefix);
        if (targetBase == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "No route registered for service: " + service);
            problem.setTitle("Service Not Found");
            problem.setType(URI.create("https://healthlife.com/errors/service-not-found"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }

        String path = request.getRequestURI();
        // Sanitise path: reject traversal sequences before forwarding to downstream services.
        // request.getRequestURI() returns the raw (encoded) URI; after decoding, any "../"
        // sequence would allow an attacker to escape the intended service path.
        if (path.contains("..") || path.contains("%2e%2e") || path.contains("%2E%2E")) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request path");
            problem.setTitle("Bad Request");
            return ResponseEntity.badRequest().body(problem);
        }
        String queryString = request.getQueryString();
        String targetUrl = targetBase + path + (queryString != null ? "?" + queryString : "");

        HttpHeaders forwardedHeaders = new HttpHeaders();
        forwardedHeaders.putAll(headers);
        // FIX: remove hop-by-hop headers that must not be forwarded
        forwardedHeaders.remove(HttpHeaders.HOST);
        forwardedHeaders.remove(HttpHeaders.CONNECTION);
        forwardedHeaders.remove("Transfer-Encoding");
        forwardedHeaders.remove("Keep-Alive");

        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null) {
            forwardedHeaders.set("X-Request-Id", requestId);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, forwardedHeaders);
        log.debug("Proxying {} {} -> {}", method, path, targetUrl);
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
                "Gateway fallback for service={} due to {}: {}",
                service,
                t.getClass().getSimpleName(),
                t.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Service " + service + " is currently unavailable. Please retry later.");
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("https://healthlife.com/errors/service-unavailable"));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

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
