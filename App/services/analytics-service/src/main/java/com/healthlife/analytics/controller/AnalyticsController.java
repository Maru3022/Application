package com.healthlife.analytics.controller;

import com.healthlife.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/events")
    public ResponseEntity<Void> trackEvent(@RequestParam UUID userId,
                                           @RequestParam String eventName,
                                           @RequestBody String properties) {
        analyticsService.trackEvent(userId, eventName, properties);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/events")
    public ResponseEntity<String> getEvent(@RequestParam UUID userId,
                                            @RequestParam String eventName) {
        return ResponseEntity.ok(analyticsService.getEvent(userId, eventName));
    }
}
