package com.healthlife.nutrition;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.nutrition.*;
import com.healthlife.common.exception.ResourceNotFoundException;
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

@SpringBootTest(classes = NutritionServiceApplication.class)
@ActiveProfiles("test")
class NutritionServiceCriticalTest {

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodLogEntryRepository foodLogEntryRepository;

    // Mock WebClient so tests don't make real HTTP calls to user-service or OpenFoodFacts
    @MockBean
    private WebClient webClient;

    private UUID userId;
    private Food testFood;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        foodLogEntryRepository.deleteAll();
        foodRepository.deleteAll();

        testFood = foodRepository.save(Food.builder()
                .name("Apple")
                .caloriesPer100g(52.0)
                .proteinPer100g(0.3)
                .carbsPer100g(14.0)
                .fatPer100g(0.2)
                .source("system")
                .build());
    }

    @Test
    void addFoodLog_shouldCalculateMacros() {
        FoodLogRequest req = FoodLogRequest.builder()
                .foodId(testFood.getId())
                .weightGrams(200.0)
                .mealType("breakfast")
                .build();
        FoodLogResponse resp = nutritionService.addFoodLog(req);
        assertThat(resp.getFoodName()).isEqualTo("Apple");
        assertThat(resp.getCalories()).isCloseTo(104.0, within(0.1));
        assertThat(resp.getProtein()).isCloseTo(0.6, within(0.1));
    }

    @Test
    void addFoodLog_nonExistentFood_shouldThrow() {
        FoodLogRequest req = FoodLogRequest.builder()
                .foodId(UUID.randomUUID())
                .weightGrams(100.0)
                .build();
        assertThatThrownBy(() -> nutritionService.addFoodLog(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchFoods_shouldFindByName() {
        foodRepository.save(Food.builder()
                .name("Banana")
                .caloriesPer100g(89.0)
                .source("system")
                .build());
        List<FoodDto> results = nutritionService.searchFoods("ban");
        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(f -> "Banana".equals(f.getName()))).isTrue();
    }

    @Test
    void getFoodByBarcode_shouldFind() {
        foodRepository.save(Food.builder()
                .name("Snickers")
                .barcode("5000159407236")
                .caloriesPer100g(500.0)
                .source("system")
                .build());
        FoodDto result = nutritionService.getFoodByBarcode("5000159407236");
        assertThat(result.getName()).isEqualTo("Snickers");
    }

    @Test
    void getFoodByBarcode_notFound_shouldThrow() {
        assertThatThrownBy(() -> nutritionService.getFoodByBarcode("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCustomFood_shouldPersistWithUserId() {
        CustomFoodRequest req = CustomFoodRequest.builder()
                .name("My Recipe")
                .caloriesPer100g(250.0)
                .proteinPer100g(10.0)
                .carbsPer100g(30.0)
                .fatPer100g(8.0)
                .build();
        FoodDto result = nutritionService.createCustomFood(req);
        assertThat(result.getName()).isEqualTo("My Recipe");
        assertThat(result.getSource()).isEqualTo("custom");
    }

    @Test
    void getCustomFoods_shouldReturnOnlyUserFoods() {
        foodRepository.save(
                Food.builder().name("User Food").source("custom").userId(userId).build());
        foodRepository.save(Food.builder().name("System Food").source("system").build());

        List<FoodDto> custom = nutritionService.getCustomFoods();
        assertThat(custom).hasSize(1);
        assertThat(custom.get(0).getName()).isEqualTo("User Food");
    }

    @Test
    void getFoodLogToday_shouldReturnTodayOnly() {
        FoodLogRequest req = FoodLogRequest.builder()
                .foodId(testFood.getId())
                .weightGrams(150.0)
                .mealType("lunch")
                .build();
        nutritionService.addFoodLog(req);

        List<FoodLogResponse> today = nutritionService.getFoodLogToday();
        assertThat(today).hasSize(1);
        assertThat(today.get(0).getWeightGrams()).isEqualTo(150.0);
    }
}
