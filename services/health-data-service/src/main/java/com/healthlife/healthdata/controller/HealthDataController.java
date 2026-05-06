package com.healthlife.healthdata.controller;

import com.healthlife.common.dto.health.*;
import com.healthlife.healthdata.service.HealthDataService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthDataController {

    private final HealthDataService healthDataService;

    @PostMapping("/sleep")
    public ResponseEntity<SleepResponse> createSleep(@Valid @RequestBody SleepRequest request) {
        return ResponseEntity.ok(healthDataService.createSleep(request));
    }

    @GetMapping("/sleep")
    public ResponseEntity<List<SleepResponse>> getSleepEntries(
            @RequestParam(required = false) OffsetDateTime from, @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(healthDataService.getSleepEntries(from, to));
    }

    @GetMapping("/sleep/stats")
    public ResponseEntity<Void> getSleepStats() {
        // Sleep stats aggregation is not yet implemented.
        // Returns 200 with empty body to avoid breaking clients.
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sleep/{id}")
    public ResponseEntity<Void> deleteSleep(@PathVariable UUID id) {
        healthDataService.deleteSleep(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activity/sync")
    public ResponseEntity<ActivityEntryDto> syncActivity(@Valid @RequestBody ActivityEntryDto dto) {
        return ResponseEntity.ok(healthDataService.syncActivity(dto));
    }

    @GetMapping("/activity/today")
    public ResponseEntity<ActivityEntryDto> getActivityToday() {
        return ResponseEntity.ok(healthDataService.getActivityToday());
    }

    @GetMapping("/activity/history")
    public ResponseEntity<List<ActivityEntryDto>> getActivityHistory() {
        return ResponseEntity.ok(healthDataService.getActivityHistory());
    }

    @PostMapping("/weight")
    public ResponseEntity<WeightResponse> createWeight(@Valid @RequestBody WeightRequest request) {
        return ResponseEntity.ok(healthDataService.createWeight(request));
    }

    @GetMapping("/weight/history")
    public ResponseEntity<List<WeightResponse>> getWeightHistory() {
        return ResponseEntity.ok(healthDataService.getWeightHistory());
    }

    @PostMapping("/water")
    public ResponseEntity<WaterResponse> addWater(@Valid @RequestBody WaterRequest request) {
        return ResponseEntity.ok(healthDataService.addWater(request));
    }

    @GetMapping("/water/today")
    public ResponseEntity<Integer> getWaterToday() {
        return ResponseEntity.ok(healthDataService.getWaterToday());
    }

    @PostMapping("/symptoms")
    public ResponseEntity<SymptomResponse> createSymptom(@Valid @RequestBody SymptomRequest request) {
        return ResponseEntity.ok(healthDataService.createSymptom(request));
    }

    @GetMapping("/symptoms/triggers")
    public ResponseEntity<Void> getSymptomTriggers() {
        // Symptom trigger analysis is not yet implemented.
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cycle")
    public ResponseEntity<CycleResponse> createCycle(@Valid @RequestBody CycleRequest request) {
        return ResponseEntity.ok(healthDataService.createCycle(request));
    }

    @GetMapping("/cycle/prediction")
    public ResponseEntity<CycleResponse> getCyclePrediction() {
        return ResponseEntity.ok(healthDataService.getCyclePrediction());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> getDashboard() {
        return ResponseEntity.ok(healthDataService.getDashboard());
    }
}
