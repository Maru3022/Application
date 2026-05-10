package com.healthlife.nutrition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.nutrition.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.nutrition.entity.Food;
import com.healthlife.nutrition.entity.FoodLogEntry;
import com.healthlife.nutrition.repository.FoodLogEntryRepository;
import com.healthlife.nutrition.repository.FoodRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class NutritionService {

    private final FoodLogEntryRepository foodLogEntryRepository;
    private final FoodRepository foodRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${internal.user-service.url:http://user-service:8082}")
    private String userServiceUrl;

    public NutritionService(
            FoodLogEntryRepository foodLogEntryRepository,
            FoodRepository foodRepository,
            ObjectMapper objectMapper,
            WebClient webClient) {
        this.foodLogEntryRepository = foodLogEntryRepository;
        this.foodRepository = foodRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    /** Minimum local results before falling back to OpenFoodFacts. */
    private static final int LOCAL_RESULTS_THRESHOLD = 5;
    /** OpenFoodFacts free API — no key required. */
    private static final String OFF_BASE_URL = "https://world.openfoodfacts.org";

    private final WebClient offWebClient = WebClient.builder()
            .baseUrl(OFF_BASE_URL)
            .defaultHeader("User-Agent", "HealthLife/1.0 (https://healthlife.com)")
            .build();

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

    /**
     * Searches for foods by name.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Search local DB first (fast, no network).
     *   <li>If fewer than {@value #LOCAL_RESULTS_THRESHOLD} results found, call OpenFoodFacts API
     *       (free, no key required) and persist new results to local DB for future queries.
     * </ol>
     */
    public List<FoodDto> searchFoods(String query) {
        List<Food> localResults = foodRepository
                .findByNameContainingIgnoreCase(query, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent();

        if (localResults.size() >= LOCAL_RESULTS_THRESHOLD) {
            return localResults.stream().map(this::mapFoodDto).toList();
        }

        // Supplement with OpenFoodFacts results
        List<Food> offResults = fetchFromOpenFoodFacts(query);
        List<Food> combined = new ArrayList<>(localResults);

        // Add OFF results that are not already in local DB (deduplicate by name)
        java.util.Set<String> existingNames =
                localResults.stream().map(f -> f.getName().toLowerCase()).collect(java.util.stream.Collectors.toSet());

        for (Food offFood : offResults) {
            if (!existingNames.contains(offFood.getName().toLowerCase())) {
                combined.add(offFood);
            }
        }

        return combined.stream().map(this::mapFoodDto).toList();
    }

    /**
     * Fetches food data from OpenFoodFacts API and persists results to local DB.
     * Returns empty list on any error — search degrades gracefully to local-only results.
     */
    @Transactional
    public List<Food> fetchFromOpenFoodFacts(String query) {
        try {
            String response = offWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi/search.pl")
                            .queryParam("search_terms", query)
                            .queryParam("search_simple", "1")
                            .queryParam("action", "process")
                            .queryParam("json", "1")
                            .queryParam("page_size", "20")
                            .queryParam("fields", "id,product_name,nutriments,code")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null) return List.of();

            JsonNode root = objectMapper.readTree(response);
            JsonNode products = root.path("products");
            if (!products.isArray()) return List.of();

            List<Food> saved = new ArrayList<>();
            for (JsonNode product : products) {
                String name = product.path("product_name").asText("").trim();
                if (name.isEmpty()) continue;

                JsonNode n = product.path("nutriments");
                double calories = n.path("energy-kcal_100g").asDouble(0);
                double protein = n.path("proteins_100g").asDouble(0);
                double carbs = n.path("carbohydrates_100g").asDouble(0);
                double fat = n.path("fat_100g").asDouble(0);
                String barcode = product.path("code").asText(null);

                // Skip entries with no nutritional data
                if (calories == 0 && protein == 0 && carbs == 0 && fat == 0) continue;

                // Check if already exists by barcode or name
                boolean exists = (barcode != null
                                && !barcode.isEmpty()
                                && foodRepository.findByBarcode(barcode).isPresent())
                        || foodRepository
                                .findByNameContainingIgnoreCase(
                                        name, org.springframework.data.domain.PageRequest.of(0, 1))
                                .getContent()
                                .stream()
                                .anyMatch(f -> f.getName().equalsIgnoreCase(name));

                if (!exists) {
                    Food food = Food.builder()
                            .name(name)
                            .caloriesPer100g(calories)
                            .proteinPer100g(protein)
                            .carbsPer100g(carbs)
                            .fatPer100g(fat)
                            .barcode(barcode)
                            .source("openfoodfacts")
                            .build();
                    saved.add(foodRepository.save(food));
                }
            }

            log.info("OpenFoodFacts: fetched {} new foods for query '{}'", saved.size(), query);
            return saved;
        } catch (Exception e) {
            log.warn("OpenFoodFacts search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public FoodDto getFoodById(UUID id) {
        return mapFoodDto(
                foodRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Food", "id", id)));
    }

    public FoodDto getFoodByBarcode(String barcode) {
        // Try local DB first
        return foodRepository.findByBarcode(barcode).map(this::mapFoodDto).orElseGet(() -> {
            // Try OpenFoodFacts barcode lookup
            try {
                String response = offWebClient
                        .get()
                        .uri("/api/v0/product/" + barcode + ".json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(5))
                        .block();
                if (response != null) {
                    JsonNode root = objectMapper.readTree(response);
                    if (root.path("status").asInt(0) == 1) {
                        JsonNode product = root.path("product");
                        String name = product.path("product_name").asText("").trim();
                        JsonNode n = product.path("nutriments");
                        if (!name.isEmpty()) {
                            Food food = Food.builder()
                                    .name(name)
                                    .caloriesPer100g(n.path("energy-kcal_100g").asDouble(0))
                                    .proteinPer100g(n.path("proteins_100g").asDouble(0))
                                    .carbsPer100g(n.path("carbohydrates_100g").asDouble(0))
                                    .fatPer100g(n.path("fat_100g").asDouble(0))
                                    .barcode(barcode)
                                    .source("openfoodfacts")
                                    .build();
                            return mapFoodDto(foodRepository.save(food));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("OpenFoodFacts barcode lookup failed for {}: {}", barcode, e.getMessage());
            }
            throw new ResourceNotFoundException("Food", "barcode", barcode);
        });
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

    /**
     * Returns nutrition goals. Attempts to fetch personalised goals from user-service;
     * falls back to sensible defaults if unavailable.
     */
    public NutritionGoalsDto getNutritionGoals(UUID userId) {
        // Try to fetch personalised goals from user-service
        try {
            String token = getInternalServiceToken();
            String response = webClient
                    .get()
                    .uri(userServiceUrl + "/api/v1/users/me/goals")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            if (response != null) {
                JsonNode goals = objectMapper.readTree(response);
                double dailyCalories = goals.path("dailyCalories").asDouble(0);
                double dailyProtein = goals.path("dailyProteinG").asDouble(0);
                double dailyCarbs = goals.path("dailyCarbsG").asDouble(0);
                double dailyFat = goals.path("dailyFatG").asDouble(0);
                if (dailyCalories > 0) {
                    return NutritionGoalsDto.builder()
                            .dailyCalories(dailyCalories)
                            .dailyProteinG(dailyProtein > 0 ? dailyProtein : 150.0)
                            .dailyCarbsG(dailyCarbs > 0 ? dailyCarbs : 250.0)
                            .dailyFatG(dailyFat > 0 ? dailyFat : 65.0)
                            .build();
                }
            }
        } catch (Exception e) {
            log.debug("Could not fetch goals from user-service for user={}: {}", userId, e.getMessage());
        }
        // Default goals — fallback when user-service is unavailable
        return NutritionGoalsDto.builder()
                .dailyCalories(2000.0)
                .dailyProteinG(150.0)
                .dailyCarbsG(250.0)
                .dailyFatG(65.0)
                .build();
    }

    private String getInternalServiceToken() {
        return SecurityUtils.getCurrentUserAccessToken();
    }

    private List<FoodLogResponse> buildFoodLogResponses(List<FoodLogEntry> entries) {
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
