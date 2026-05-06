package com.healthlife.nutrition.service;

import com.healthlife.common.dto.nutrition.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.nutrition.entity.Food;
import com.healthlife.nutrition.entity.FoodLogEntry;
import com.healthlife.nutrition.repository.FoodLogEntryRepository;
import com.healthlife.nutrition.repository.FoodRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final FoodLogEntryRepository foodLogEntryRepository;
    private final FoodRepository foodRepository;

    @Transactional
    public FoodLogResponse addFoodLog(FoodLogRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Food food = foodRepository
                .findById(request.getFoodId())
                .orElseThrow(() -> new ResourceNotFoundException("Food", "id", request.getFoodId()));

        double multiplier = request.getWeightGrams() / 100.0;
        FoodLogEntry entry = FoodLogEntry.builder()
                .userId(userId)
                .foodId(request.getFoodId())
                .weightGrams(request.getWeightGrams())
                .mealType(request.getMealType())
                .consumedAt(request.getConsumedAt() != null ? request.getConsumedAt() : OffsetDateTime.now())
                .build();
        entry = foodLogEntryRepository.save(entry);

        return FoodLogResponse.builder()
                .id(entry.getId())
                .foodId(food.getId())
                .foodName(food.getName())
                .weightGrams(request.getWeightGrams())
                .calories(food.getCaloriesPer100g() * multiplier)
                .protein(food.getProteinPer100g() * multiplier)
                .carbs(food.getCarbsPer100g() * multiplier)
                .fat(food.getFatPer100g() * multiplier)
                .mealType(entry.getMealType())
                .consumedAt(entry.getConsumedAt())
                .build();
    }

    public List<FoodLogResponse> getFoodLogToday() {
        UUID userId = SecurityUtils.getCurrentUserId();
        OffsetDateTime start =
                LocalDate.now().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime end = start.plusDays(1);
        return buildFoodLogResponses(
                foodLogEntryRepository.findByUserIdAndConsumedAtBetweenOrderByConsumedAtDesc(userId, start, end));
    }

    public List<FoodLogResponse> getFoodLogHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return buildFoodLogResponses(foodLogEntryRepository
                .findByUserIdOrderByConsumedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent());
    }

    @Transactional
    public void deleteFoodLog(UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        FoodLogEntry entry = foodLogEntryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FoodLogEntry", "id", id));
        if (!entry.getUserId().equals(userId)) {
            throw new com.healthlife.common.exception.ForbiddenException("Not your entry");
        }
        foodLogEntryRepository.delete(entry);
    }

    public List<FoodDto> searchFoods(String query) {
        // FIX: limit to 50 results to prevent OOM with broad searches
        return foodRepository
                .findByNameContainingIgnoreCase(query, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(this::mapFoodDto)
                .toList();
    }

    public FoodDto getFoodById(UUID id) {
        return mapFoodDto(
                foodRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Food", "id", id)));
    }

    public FoodDto getFoodByBarcode(String barcode) {
        return mapFoodDto(foodRepository
                .findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Food", "barcode", barcode)));
    }

    @Transactional
    public FoodDto createCustomFood(CustomFoodRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Food food = Food.builder()
                .name(request.getName())
                .caloriesPer100g(request.getCaloriesPer100g())
                .proteinPer100g(request.getProteinPer100g())
                .carbsPer100g(request.getCarbsPer100g())
                .fatPer100g(request.getFatPer100g())
                .source("custom")
                .userId(userId)
                .build();
        return mapFoodDto(foodRepository.save(food));
    }

    public List<FoodDto> getCustomFoods() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return foodRepository.findByUserId(userId).stream()
                .map(this::mapFoodDto)
                .toList();
    }

    /**
     * Returns aggregated macro totals for today's food log entries.
     */
    public NutritionAnalysisDto getNutritionAnalysis() {
        UUID userId = SecurityUtils.getCurrentUserId();
        OffsetDateTime start =
                LocalDate.now().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime end = start.plusDays(1);
        List<FoodLogEntry> entries =
                foodLogEntryRepository.findByUserIdAndConsumedAtBetweenOrderByConsumedAtDesc(userId, start, end);

        List<UUID> foodIds =
                entries.stream().map(FoodLogEntry::getFoodId).distinct().toList();
        Map<UUID, Food> foodMap = foodRepository.findAllById(foodIds).stream()
                .collect(java.util.stream.Collectors.toMap(Food::getId, f -> f));

        double calories = 0, protein = 0, carbs = 0, fat = 0;
        for (FoodLogEntry e : entries) {
            Food food = foodMap.get(e.getFoodId());
            if (food == null) continue;
            double m = e.getWeightGrams() / 100.0;
            calories += food.getCaloriesPer100g() * m;
            protein += food.getProteinPer100g() * m;
            carbs += food.getCarbsPer100g() * m;
            fat += food.getFatPer100g() * m;
        }

        return NutritionAnalysisDto.builder()
                .totalCalories(Math.round(calories * 10.0) / 10.0)
                .totalProteinG(Math.round(protein * 10.0) / 10.0)
                .totalCarbsG(Math.round(carbs * 10.0) / 10.0)
                .totalFatG(Math.round(fat * 10.0) / 10.0)
                .entryCount(entries.size())
                .build();
    }

    public NutritionGoalsDto getNutritionGoals(UUID userId) {
        // Goals are stored in user-service; return sensible defaults here.
        // A future iteration can call user-service via Feign/RestTemplate.
        return NutritionGoalsDto.builder()
                .dailyCalories(2000.0)
                .dailyProteinG(150.0)
                .dailyCarbsG(250.0)
                .dailyFatG(65.0)
                .build();
    }

    private List<FoodLogResponse> buildFoodLogResponses(List<FoodLogEntry> entries) {
        // FIX N+1: bulk-load all referenced foods in one query instead of N individual findById calls
        List<UUID> foodIds =
                entries.stream().map(FoodLogEntry::getFoodId).distinct().toList();
        Map<UUID, Food> foodMap = foodRepository.findAllById(foodIds).stream()
                .collect(java.util.stream.Collectors.toMap(Food::getId, f -> f));

        return entries.stream()
                .map(entry -> {
                    Food food = foodMap.get(entry.getFoodId());
                    double multiplier = entry.getWeightGrams() / 100.0;
                    return FoodLogResponse.builder()
                            .id(entry.getId())
                            .foodId(entry.getFoodId())
                            .foodName(food != null ? food.getName() : "Unknown")
                            .weightGrams(entry.getWeightGrams())
                            .calories(food != null ? food.getCaloriesPer100g() * multiplier : 0)
                            .protein(food != null ? food.getProteinPer100g() * multiplier : 0)
                            .carbs(food != null ? food.getCarbsPer100g() * multiplier : 0)
                            .fat(food != null ? food.getFatPer100g() * multiplier : 0)
                            .mealType(entry.getMealType())
                            .consumedAt(entry.getConsumedAt())
                            .build();
                })
                .toList();
    }

    private FoodDto mapFoodDto(Food f) {
        return FoodDto.builder()
                .id(f.getId())
                .name(f.getName())
                .caloriesPer100g(f.getCaloriesPer100g())
                .proteinPer100g(f.getProteinPer100g())
                .carbsPer100g(f.getCarbsPer100g())
                .fatPer100g(f.getFatPer100g())
                .barcode(f.getBarcode())
                .source(f.getSource())
                .build();
    }
}
