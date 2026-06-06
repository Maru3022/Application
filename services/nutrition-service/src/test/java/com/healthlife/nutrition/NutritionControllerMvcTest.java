package com.healthlife.nutrition;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.nutrition.*;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.nutrition.service.NutritionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    NutritionService nutritionService;

    @MockBean
    WebClient webClient;

    private UUID userId = UUID.randomUUID();
    private String jwtToken;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        jwtToken = jwtTokenProvider.generateAccessToken(userId, "test@health.com", "USER");
    }

    @Test
    void addFoodLog_shouldReturn200() throws Exception {
        FoodLogResponse mockResponse = FoodLogResponse.builder()
                .id(UUID.randomUUID())
                .foodName("Apple")
                .calories(52.0)
                .build();
        when(nutritionService.addFoodLog(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/nutrition/food-log")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                            {"foodId":"%s","weightGrams":100.0,"mealType":"breakfast"}
                            """
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void getFoodLogToday_shouldReturn200() throws Exception {
        when(nutritionService.getFoodLogToday()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/nutrition/food-log/today").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getFoodLogHistory_shouldReturn200() throws Exception {
        when(nutritionService.getFoodLogHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/nutrition/food-log/history").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void deleteFoodLog_shouldReturn204() throws Exception {
        doNothing().when(nutritionService).deleteFoodLog(any());

        mockMvc.perform(delete("/api/v1/nutrition/food-log/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void searchFoods_shouldReturn200() throws Exception {
        when(nutritionService.searchFoods(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .param("q", "apple")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getFoodById_shouldReturn200() throws Exception {
        FoodDto mockFood = FoodDto.builder().id(UUID.randomUUID()).name("Apple").build();
        when(nutritionService.getFoodById(any())).thenReturn(mockFood);

        mockMvc.perform(get("/api/v1/nutrition/foods/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getFoodByBarcode_shouldReturn200() throws Exception {
        FoodDto mockFood = FoodDto.builder().id(UUID.randomUUID()).name("Apple").build();
        when(nutritionService.getFoodByBarcode(anyString())).thenReturn(mockFood);

        mockMvc.perform(post("/api/v1/nutrition/foods/barcode")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("1234567890"))
                .andExpect(status().isOk());
    }

    @Test
    void recognizeFoodByPhoto_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/nutrition/foods/photo").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getCustomFoods_shouldReturn200() throws Exception {
        when(nutritionService.getCustomFoods()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/nutrition/foods/custom").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void createCustomFood_shouldReturn200() throws Exception {
        FoodDto mockFood =
                FoodDto.builder().id(UUID.randomUUID()).name("Custom").build();
        when(nutritionService.createCustomFood(any())).thenReturn(mockFood);

        mockMvc.perform(
                        post("/api/v1/nutrition/foods/custom")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"name":"Custom Food","caloriesPer100g":100.0,"proteinPer100g":10.0,"carbsPer100g":20.0,"fatPer100g":5.0}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void getNutritionAnalysis_shouldReturn200() throws Exception {
        NutritionAnalysisDto mockAnalysis =
                NutritionAnalysisDto.builder().entryCount(0).build();
        when(nutritionService.getNutritionAnalysis()).thenReturn(mockAnalysis);

        mockMvc.perform(get("/api/v1/nutrition/analysis").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void getNutritionGoals_shouldReturn200() throws Exception {
        NutritionGoalsDto mockGoals =
                NutritionGoalsDto.builder().dailyCalories(2000.0).build();
        when(nutritionService.getNutritionGoals(any())).thenReturn(mockGoals);

        mockMvc.perform(get("/api/v1/nutrition/goals").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }
}
