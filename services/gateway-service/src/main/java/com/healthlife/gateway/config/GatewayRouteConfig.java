package com.healthlife.gateway.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Simple reverse-proxy gateway that routes external requests to internal microservices.
 * Each downstream call is protected by Resilience4j circuit breaker, retry, and rate limiter.
 * Fallback methods return RFC 7807 ProblemDetail responses when a service is unavailable.
 *
 * FIX: Routes now use Kubernetes service names instead of localhost.
 * FIX: RestTemplate configured with connection/read timeouts to prevent hanging.
 */
@Component
@RestController
public class GatewayRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

    private final RestTemplate restTemplate;

    // FIX: Service URLs now read from env vars (default = K8s service names)
    private final Map<String, String> routes;

    public GatewayRouteConfig(
            @Value("${services.auth.url:http://auth-service}") String authUrl,
            @Value("${services.user.url:http://user-service}") String userUrl,
            @Value("${services.health.url:http://health-data-service}") String healthUrl,
            @Value("${services.mental.url:http://mental-service}") String mentalUrl,
            @Value("${services.nutrition.url:http://nutrition-service}") String nutritionUrl,
            @Value("${services.ai.url:http://ai-coach-service}") String aiUrl,
            @Value("${services.social.url:http://social-service}") String socialUrl,
            @Value("${services.notification.url:http://notification-service}") String notificationUrl,
            @Value("${services.analytics.url:http://analytics-service}") String analyticsUrl,
            RestTemplateBuilder restTemplateBuilder) {

        // FIX: timeout configured to prevent indefinite hangs
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        this.routes = Map.of(
                "/api/v1/auth",          authUrl,
                "/api/v1/users",         userUrl,
                "/api/v1/health",        healthUrl,
                "/api/v1/mental",        mentalUrl,
                "/api/v1/nutrition",     nutritionUrl,
                "/api/v1/ai",            aiUrl,
                "/api/v1/social",        socialUrl,
                "/api/v1/notifications", notificationUrl,
                "/api/v1/analytics",     analyticsUrl);
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
        log.warn("Gateway fallback for service={} due to {}: {}", service, t.getClass().getSimpleName(), t.getMessage());
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
