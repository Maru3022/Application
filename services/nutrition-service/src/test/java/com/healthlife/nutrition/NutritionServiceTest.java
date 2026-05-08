package com.healthlife.nutrition;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.nutrition.*;
import com.healthlife.nutrition.entity.Food;
import com.healthlife.nutrition.entity.FoodLogEntry;
import com.healthlife.nutrition.repository.FoodLogEntryRepository;
import com.healthlife.nutrition.repository.FoodRepository;
import com.healthlife.nutrition.service.NutritionService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = NutritionServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class NutritionServiceTest {

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodLogEntryRepository foodLogEntryRepository;

    private UUID userId;
    private Food apple;
    private Food chicken;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        foodLogEntryRepository.deleteAll();
        foodRepository.deleteAll();

        apple = foodRepository.save(Food.builder()
                .name("Apple")
                .caloriesPer100g(52.0)
                .proteinPer100g(0.3)
                .carbsPer100g(14.0)
                .fatPer100g(0.2)
                .source("system")
                .build());

        chicken = foodRepository.save(Food.builder()
                .name("Chicken Breast")
                .caloriesPer100g(165.0)
                .proteinPer100g(31.0)
                .carbsPer100g(0.0)
                .fatPer100g(3.6)
                .barcode("1234567890")
                .source("system")
                .build());
    }

    // ── Food search ───────────────────────────────────────────────────────────

    @Test
    void searchFoods_shouldReturnMatchingFoods() {
        List<FoodDto> results = nutritionService.searchFoods("apple");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Apple");
    }

    @Test
    void searchFoods_caseInsensitive() {
        List<FoodDto> results = nutritionService.searchFoods("CHICKEN");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Chicken Breast");
    }

    @Test
    void getFoodByBarcode_shouldReturnFood() {
        FoodDto result = nutritionService.getFoodByBarcode("1234567890");
        assertThat(result.getName()).isEqualTo("Chicken Breast");
    }

    @Test
    void getFoodById_shouldReturnFood() {
        FoodDto result = nutritionService.getFoodById(apple.getId());
        assertThat(result.getName()).isEqualTo("Apple");
        assertThat(result.getCaloriesPer100g()).isEqualTo(52.0);
    }

    // ── Custom foods ──────────────────────────────────────────────────────────

    @Test
    void createCustomFood_shouldPersistAndReturnDto() {
        CustomFoodRequest req = CustomFoodRequest.builder()
                .name("Homemade Granola")
                .caloriesPer100g(450.0)
                .proteinPer100g(10.0)
                .carbsPer100g(65.0)
                .fatPer100g(15.0)
                .build();

        FoodDto result = nutritionService.createCustomFood(req);

        assertThat(result.getName()).isEqualTo("Homemade Granola");
        assertThat(result.getSource()).isEqualTo("custom");
    }

    @Test
    void getCustomFoods_shouldReturnOnlyUserFoods() {
        nutritionService.createCustomFood(CustomFoodRequest.builder()
                .name("My Protein Shake")
                .caloriesPer100g(120.0)
                .proteinPer100g(25.0)
                .carbsPer100g(5.0)
                .fatPer100g(2.0)
                .build());

        List<FoodDto> custom = nutritionService.getCustomFoods();
        assertThat(custom).hasSize(1);
        assertThat(custom.get(0).getName()).isEqualTo("My Protein Shake");
    }

    // ── Food log ──────────────────────────────────────────────────────────────

    @Test
    void addFoodLog_shouldCalculateMacros() {
        FoodLogRequest req = FoodLogRequest.builder()
                .foodId(apple.getId())
                .weightGrams(200.0) // 2× 100g
                .mealType("breakfast")
                .consumedAt(OffsetDateTime.now())
                .build();

        FoodLogResponse resp = nutritionService.addFoodLog(req);

        assertThat(resp.getFoodName()).isEqualTo("Apple");
        assertThat(resp.getCalories()).isEqualTo(104.0); // 52 * 2
        assertThat(resp.getProtein()).isEqualTo(0.6); // 0.3 * 2
        assertThat(resp.getCarbs()).isEqualTo(28.0); // 14 * 2
        assertThat(resp.getMealType()).isEqualTo("breakfast");
    }

    @Test
    void getFoodLogToday_shouldReturnTodaysEntries() {
        foodLogEntryRepository.save(FoodLogEntry.builder()
                .userId(userId)
                .foodId(apple.getId())
                .weightGrams(150.0)
                .mealType("lunch")
                .consumedAt(OffsetDateTime.now())
                .build());

        foodLogEntryRepository.save(FoodLogEntry.builder()
                .userId(userId)
                .foodId(chicken.getId())
                .weightGrams(200.0)
                .mealType("dinner")
                .consumedAt(OffsetDateTime.now().minusDays(1)) // yesterday — should NOT appear
                .build());

        List<FoodLogResponse> today = nutritionService.getFoodLogToday();
        assertThat(today).hasSize(1);
        assertThat(today.get(0).getMealType()).isEqualTo("lunch");
    }

    @Test
    void deleteFoodLog_shouldRemoveEntry() {
        FoodLogEntry entry = foodLogEntryRepository.save(FoodLogEntry.builder()
                .userId(userId)
                .foodId(apple.getId())
                .weightGrams(100.0)
                .mealType("snack")
                .consumedAt(OffsetDateTime.now())
                .build());

        nutritionService.deleteFoodLog(entry.getId());

        assertThat(foodLogEntryRepository.findById(entry.getId())).isEmpty();
    }

    // ── Nutrition analysis ────────────────────────────────────────────────────

    @Test
    void getNutritionAnalysis_shouldAggregateTodaysMacros() {
        foodLogEntryRepository.save(FoodLogEntry.builder()
                .userId(userId)
                .foodId(apple.getId())
                .weightGrams(100.0)
                .mealType("breakfast")
                .consumedAt(OffsetDateTime.now())
                .build());

        foodLogEntryRepository.save(FoodLogEntry.builder()
                .userId(userId)
                .foodId(chicken.getId())
                .weightGrams(100.0)
                .mealType("lunch")
                .consumedAt(OffsetDateTime.now())
                .build());

        NutritionAnalysisDto analysis = nutritionService.getNutritionAnalysis();

        assertThat(analysis.getEntryCount()).isEqualTo(2);
        assertThat(analysis.getTotalCalories()).isEqualTo(217.0); // 52 + 165
        assertThat(analysis.getTotalProteinG()).isEqualTo(31.3); // 0.3 + 31
    }
}
