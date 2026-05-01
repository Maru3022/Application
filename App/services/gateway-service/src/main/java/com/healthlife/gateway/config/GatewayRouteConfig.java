package com.healthlife.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RestController
public class GatewayRouteConfig {

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
            "/api/v1/analytics", "http://localhost:8089"
    );

    @RequestMapping("/api/v1/{service}/**")
    public ResponseEntity<String> proxyRequest(
            @PathVariable String service,
            HttpServletRequest request,
            @RequestBody(required = false) String body,
            @RequestHeader HttpHeaders headers,
            HttpMethod method) {

        String prefix = "/api/v1/" + service;
        String targetBase = ROUTES.get(prefix);
        if (targetBase == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"Service not found: " + service + "\"}");
        }

        String path = request.getRequestURI();
        String targetUrl = targetBase + path + 
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        HttpHeaders forwardedHeaders = new HttpHeaders();
        forwardedHeaders.putAll(headers);
        forwardedHeaders.remove("host");

        HttpEntity<String> entity = new HttpEntity<>(body, forwardedHeaders);
        try {
            return restTemplate.exchange(targetUrl, method, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Service unavailable: " + service + "\"}");
        }
    }
}
