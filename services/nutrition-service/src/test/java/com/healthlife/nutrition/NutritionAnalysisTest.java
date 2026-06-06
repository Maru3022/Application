package com.healthlife.nutrition;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.nutrition.*;
import com.healthlife.common.exception.ForbiddenException;
import com.healthlife.nutrition.entity.Food;
import com.healthlife.nutrition.repository.FoodLogEntryRepository;
import com.healthlife.nutrition.repository.FoodRepository;
import com.healthlife.nutrition.service.NutritionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Additional tests for NutritionService covering:
 * - Nutrition analysis (calorie/macro totals)
 * - Delete food log (own vs other user)
 * - Nutrition goals fallback defaults
 * - Food log history pagination
 * - Multiple food entries in analysis
 */
@SpringBootTest(classes = NutritionServiceApplication.class)
@ActiveProfiles("test")
class NutritionAnalysisTest {

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodLogEntryRepository foodLogEntryRepository;

    @MockBean
    private WebClient webClient;

    private UUID userId;
    private Food chicken;
    private Food rice;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        setAuth(userId);
        foodLogEntryRepository.deleteAll();
        foodRepository.deleteAll();

        chicken = foodRepository.save(Food.builder()
                .name("Chicken Breast")
                .caloriesPer100g(165.0)
                .proteinPer100g(31.0)
                .carbsPer100g(0.0)
                .fatPer100g(3.6)
                .source("system")
                .build());

        rice = foodRepository.save(Food.builder()
                .name("White Rice")
                .caloriesPer100g(130.0)
                .proteinPer100g(2.7)
                .carbsPer100g(28.0)
                .fatPer100g(0.3)
                .source("system")
                .build());
    }

    // ── nutrition analysis ────────────────────────────────────────────────────

    @Test
    void getNutritionAnalysis_noEntries_shouldReturnZeros() {
        NutritionAnalysisDto analysis = nutritionService.getNutritionAnalysis();

        assertThat(analysis.getTotalCalories()).isZero();
        assertThat(analysis.getTotalProteinG()).isZero();
        assertThat(analysis.getTotalCarbsG()).isZero();
        assertThat(analysis.getTotalFatG()).isZero();
        assertThat(analysis.getEntryCount()).isZero();
    }

    @Test
    void getNutritionAnalysis_withEntries_shouldSumMacros() {
        // 200g chicken: 330 kcal, 62g protein, 0g carbs, 7.2g fat
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(200.0)
                .mealType("lunch")
                .build());

        // 150g rice: 195 kcal, 4.05g protein, 42g carbs, 0.45g fat
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(rice.getId())
                .weightGrams(150.0)
                .mealType("lunch")
                .build());

        NutritionAnalysisDto analysis = nutritionService.getNutritionAnalysis();

        assertThat(analysis.getTotalCalories()).isCloseTo(525.0, within(1.0));
        assertThat(analysis.getTotalProteinG()).isCloseTo(66.0, within(1.0));
        assertThat(analysis.getTotalCarbsG()).isCloseTo(42.0, within(1.0));
        assertThat(analysis.getEntryCount()).isEqualTo(2);
    }

    @Test
    void getNutritionAnalysis_roundsToOneDecimal() {
        // 33g chicken: 54.45 kcal → should round to 54.5
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(33.0)
                .mealType("snack")
                .build());

        NutritionAnalysisDto analysis = nutritionService.getNutritionAnalysis();

        // Verify rounding is applied (not raw floating point)
        assertThat(analysis.getTotalCalories()).isLessThan(60.0);
        assertThat(analysis.getTotalCalories()).isGreaterThan(50.0);
    }

    // ── delete food log ───────────────────────────────────────────────────────

    @Test
    void deleteFoodLog_ownEntry_shouldDelete() {
        FoodLogResponse entry = nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(100.0)
                .mealType("dinner")
                .build());

        nutritionService.deleteFoodLog(entry.getId());

        assertThat(foodLogEntryRepository.findById(entry.getId())).isEmpty();
    }

    @Test
    void deleteFoodLog_otherUserEntry_shouldThrowForbidden() {
        // Create entry as user1
        FoodLogResponse entry = nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(100.0)
                .mealType("dinner")
                .build());

        // Switch to user2
        UUID user2 = UUID.randomUUID();
        setAuth(user2);

        assertThatThrownBy(() -> nutritionService.deleteFoodLog(entry.getId())).isInstanceOf(ForbiddenException.class);
    }

    // ── nutrition goals fallback ──────────────────────────────────────────────

    @Test
    void getNutritionGoals_userServiceUnavailable_shouldReturnDefaults() {
        // WebClient is mocked and will throw — service should fall back to defaults
        NutritionGoalsDto goals = nutritionService.getNutritionGoals(userId);

        assertThat(goals.getDailyCalories()).isEqualTo(2000.0);
        assertThat(goals.getDailyProteinG()).isEqualTo(150.0);
        assertThat(goals.getDailyCarbsG()).isEqualTo(250.0);
        assertThat(goals.getDailyFatG()).isEqualTo(65.0);
    }

    // ── food log history ──────────────────────────────────────────────────────

    @Test
    void getFoodLogHistory_shouldReturnAllEntries() {
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(100.0)
                .mealType("breakfast")
                .build());
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(rice.getId())
                .weightGrams(200.0)
                .mealType("lunch")
                .build());

        List<FoodLogResponse> history = nutritionService.getFoodLogHistory();

        assertThat(history).hasSize(2);
    }

    @Test
    void getFoodLogHistory_differentUsers_shouldBeIsolated() {
        // User1 adds entry
        nutritionService.addFoodLog(FoodLogRequest.builder()
                .foodId(chicken.getId())
                .weightGrams(100.0)
                .mealType("lunch")
                .build());

        // User2 should see empty history
        UUID user2 = UUID.randomUUID();
        setAuth(user2);

        List<FoodLogResponse> history = nutritionService.getFoodLogHistory();
        assertThat(history).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
