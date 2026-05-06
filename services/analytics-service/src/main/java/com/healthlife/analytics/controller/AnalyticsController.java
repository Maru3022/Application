package com.healthlife.analytics.controller;

import com.healthlife.analytics.service.AnalyticsService;
import com.healthlife.common.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Track an analytics event for the currently authenticated user.
     * The userId is taken from the JWT — clients cannot spoof it.
     */
    @PostMapping("/events")
    public ResponseEntity<Void> trackEvent(
            @RequestParam @NotBlank String eventName,
            @RequestBody(required = false) String properties) {
        UUID userId = SecurityUtils.getCurrentUserId();
        analyticsService.trackEvent(userId, eventName, properties);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieve all recorded occurrences of an event for the current user.
     */
    @GetMapping("/events")
    public ResponseEntity<List<String>> getEvents(@RequestParam @NotBlank String eventName) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.getEvents(userId, eventName));
    }
}
