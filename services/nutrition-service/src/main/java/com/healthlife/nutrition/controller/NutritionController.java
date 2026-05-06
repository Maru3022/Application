package com.healthlife.nutrition.controller;

import com.healthlife.common.dto.nutrition.*;
import com.healthlife.nutrition.service.NutritionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @PostMapping("/food-log")
    public ResponseEntity<FoodLogResponse> addFoodLog(@Valid @RequestBody FoodLogRequest request) {
        return ResponseEntity.ok(nutritionService.addFoodLog(request));
    }

    @GetMapping("/food-log/today")
    public ResponseEntity<List<FoodLogResponse>> getFoodLogToday() {
        return ResponseEntity.ok(nutritionService.getFoodLogToday());
    }

    @GetMapping("/food-log/history")
    public ResponseEntity<List<FoodLogResponse>> getFoodLogHistory() {
        return ResponseEntity.ok(nutritionService.getFoodLogHistory());
    }

    @DeleteMapping("/food-log/{id}")
    public ResponseEntity<Void> deleteFoodLog(@PathVariable UUID id) {
        nutritionService.deleteFoodLog(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/foods/search")
    public ResponseEntity<List<FoodDto>> searchFoods(@RequestParam String q) {
        return ResponseEntity.ok(nutritionService.searchFoods(q));
    }

    @GetMapping("/foods/{id}")
    public ResponseEntity<FoodDto> getFoodById(@PathVariable UUID id) {
        return ResponseEntity.ok(nutritionService.getFoodById(id));
    }

    @PostMapping("/foods/barcode")
    public ResponseEntity<FoodDto> getFoodByBarcode(@RequestBody String barcode) {
        return ResponseEntity.ok(nutritionService.getFoodByBarcode(barcode));
    }

    @PostMapping("/foods/photo")
    public ResponseEntity<Void> recognizeFoodByPhoto() {
        // Food photo recognition is not yet implemented.
        return ResponseEntity.ok().build();
    }

    @GetMapping("/foods/custom")
    public ResponseEntity<List<FoodDto>> getCustomFoods() {
        return ResponseEntity.ok(nutritionService.getCustomFoods());
    }

    @PostMapping("/foods/custom")
    public ResponseEntity<FoodDto> createCustomFood(@Valid @RequestBody CustomFoodRequest request) {
        return ResponseEntity.ok(nutritionService.createCustomFood(request));
    }

    @GetMapping("/analysis")
    public ResponseEntity<Void> getNutritionAnalysis() {
        // Nutrition analysis aggregation is not yet implemented.
        return ResponseEntity.ok().build();
    }

    @GetMapping("/goals")
    public ResponseEntity<Void> getNutritionGoals() {
        // Nutrition goals are not yet implemented.
        return ResponseEntity.ok().build();
    }
}
