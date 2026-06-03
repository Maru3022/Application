package com.healthlife.mental.controller;

import com.healthlife.common.dto.mental.*;
import com.healthlife.mental.service.MentalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mental")
@RequiredArgsConstructor
@Validated
public class MentalController {

    private final MentalService mentalService;

    @PostMapping("/mood")
    public ResponseEntity<MoodResponse> createMood(@Valid @RequestBody MoodRequest request) {
        return ResponseEntity.ok(mentalService.createMood(request));
    }

    @GetMapping("/mood/history")
    public ResponseEntity<List<MoodResponse>> getMoodHistory() {
        return ResponseEntity.ok(mentalService.getMoodHistory());
    }

    @GetMapping("/mood/patterns")
    public ResponseEntity<Void> getMoodPatterns() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/journal")
    public ResponseEntity<JournalResponse> createJournal(@Valid @RequestBody JournalRequest request) {
        return ResponseEntity.ok(mentalService.createJournal(request));
    }

    @GetMapping("/journal")
    public ResponseEntity<List<JournalResponse>> getJournals() {
        return ResponseEntity.ok(mentalService.getJournals());
    }

    @GetMapping("/journal/themes")
    public ResponseEntity<Void> getJournalThemes() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stress")
    public ResponseEntity<StressResponse> createStress(@Valid @RequestBody StressRequest request) {
        return ResponseEntity.ok(mentalService.createStress(request));
    }

    @GetMapping("/stress/stats")
    public ResponseEntity<List<StressResponse>> getStressStats() {
        return ResponseEntity.ok(mentalService.getStressStats());
    }

    @GetMapping("/meditations")
    public ResponseEntity<List<MeditationDto>> getMeditations(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(mentalService.getMeditations(category));
    }

    @PostMapping("/meditations/{id}/complete")
    public ResponseEntity<Void> completeMeditation(@PathVariable UUID id) {
        mentalService.completeMeditation(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/meditations/recommended")
    public ResponseEntity<MeditationDto> getRecommendedMeditation() {
        return ResponseEntity.ok(mentalService.getRecommendedMeditation());
    }

    @PostMapping("/breathing/session")
    public ResponseEntity<Void> createBreathingSession(@Valid @RequestBody BreathingSessionRequest request) {
        mentalService.createBreathingSession(request);
        return ResponseEntity.ok().build();
    }
}
