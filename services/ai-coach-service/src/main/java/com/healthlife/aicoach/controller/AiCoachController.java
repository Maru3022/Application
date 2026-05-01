package com.healthlife.aicoach.controller;

import com.healthlife.aicoach.service.AiCoachService;
import com.healthlife.common.dto.aicoach.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiCoachController {

    private final AiCoachService aiCoachService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(aiCoachService.chat(request));
    }

    @GetMapping("/insights/daily")
    public ResponseEntity<InsightDto> getDailyInsight() {
        return ResponseEntity.ok(aiCoachService.getDailyInsight());
    }

    @GetMapping("/insights/weekly")
    public ResponseEntity<InsightDto> getWeeklyInsight() {
        return ResponseEntity.ok(aiCoachService.getWeeklyInsight());
    }

    @GetMapping("/predictions/energy")
    public ResponseEntity<InsightDto> getEnergyPrediction() {
        return ResponseEntity.ok(aiCoachService.getEnergyPrediction());
    }

    @GetMapping("/predictions/symptoms")
    public ResponseEntity<InsightDto> getSymptomPrediction() {
        return ResponseEntity.ok(aiCoachService.getSymptomPrediction());
    }

    @GetMapping("/recommendations")
    public ResponseEntity<InsightDto> getRecommendations() {
        return ResponseEntity.ok(aiCoachService.getRecommendations());
    }

    @PostMapping("/analyze/correlations")
    public ResponseEntity<InsightDto> analyzeCorrelations() {
        return ResponseEntity.ok(aiCoachService.analyzeCorrelations());
    }
}
