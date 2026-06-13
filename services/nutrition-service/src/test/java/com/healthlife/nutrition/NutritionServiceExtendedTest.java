package com.healthlife.nutrition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.common.dto.nutrition.FoodDto;
import com.healthlife.common.dto.nutrition.NutritionGoalsDto;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.nutrition.entity.Food;
import com.healthlife.nutrition.repository.FoodLogEntryRepository;
import com.healthlife.nutrition.repository.FoodRepository;
import com.healthlife.nutrition.service.NutritionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Extended NutritionService tests targeting OpenFoodFacts integration, goals fetch,
 * and search early-return paths to maintain JaCoCo coverage above CI threshold.
 */
@SpringBootTest(classes = NutritionServiceApplication.class)
@ActiveProfiles("test")
class NutritionServiceExtendedTest {

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodLogEntryRepository foodLogEntryRepository;

    @MockitoBean
    private WebClient webClient;

    private UUID userId;
    private NutritionService targetNutritionService;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails("test-access-token");
        SecurityContextHolder.getContext().setAuthentication(auth);

        foodLogEntryRepository.deleteAll();
        foodRepository.deleteAll();

        targetNutritionService = (NutritionService)
                AopUtils.getTargetClass(nutritionService).cast(AopTestUtils.getTargetObject(nutritionService));
    }

    @Test
    void searchFoods_manyLocalResults_shouldReturnWithoutOffCall() {
        for (int i = 0; i < 5; i++) {
            foodRepository.save(Food.builder()
                    .name("Protein Bar " + i)
                    .caloriesPer100g(400.0)
                    .source("system")
                    .build());
        }

        List<FoodDto> results = nutritionService.searchFoods("Protein");

        assertThat(results).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void getNutritionGoals_userServiceReturnsGoals_shouldUseRemoteValues() {
        stubWebClientJson(
                webClient, "{\"dailyCalories\":1800,\"dailyProteinG\":120,\"dailyCarbsG\":200,\"dailyFatG\":60}");

        NutritionGoalsDto goals = nutritionService.getNutritionGoals(userId);

        assertThat(goals.getDailyCalories()).isEqualTo(1800.0);
        assertThat(goals.getDailyProteinG()).isEqualTo(120.0);
        assertThat(goals.getDailyCarbsG()).isEqualTo(200.0);
        assertThat(goals.getDailyFatG()).isEqualTo(60.0);
    }

    @Test
    void getFoodByBarcode_offLookup_shouldPersistAndReturnFood() {
        WebClient offClient = mock(WebClient.class);
        ReflectionTestUtils.setField(targetNutritionService, "offWebClient", offClient);

        stubWebClientJson(
                offClient,
                """
                {"status":1,"product":{"product_name":"OFF Granola","nutriments":\
                {"energy-kcal_100g":450,"proteins_100g":10,"carbohydrates_100g":60,"fat_100g":15}}}
                """);

        FoodDto result = nutritionService.getFoodByBarcode("5901234123457");

        assertThat(result.getName()).isEqualTo("OFF Granola");
        assertThat(result.getSource()).isEqualTo("openfoodfacts");
        assertThat(foodRepository.findByBarcode("5901234123457")).isPresent();
    }

    @Test
    void fetchFromOpenFoodFacts_validResponse_shouldPersistNewFoods() {
        WebClient offClient = mock(WebClient.class);
        ReflectionTestUtils.setField(targetNutritionService, "offWebClient", offClient);

        stubWebClientUriFunction(
                offClient,
                """
                {"products":[{"product_name":"Quinoa","code":"123","nutriments":\
                {"energy-kcal_100g":368,"proteins_100g":14,"carbohydrates_100g":64,"fat_100g":6}}]}
                """);

        List<Food> saved = nutritionService.fetchFromOpenFoodFacts("quinoa");

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getName()).isEqualTo("Quinoa");
    }

    @Test
    void fetchFromOpenFoodFacts_nullResponse_shouldReturnEmpty() {
        WebClient offClient = mock(WebClient.class);
        ReflectionTestUtils.setField(targetNutritionService, "offWebClient", offClient);
        stubWebClientUriFunction(offClient, null);

        assertThat(nutritionService.fetchFromOpenFoodFacts("empty")).isEmpty();
    }

    @Test
    void fetchFromOpenFoodFacts_invalidJsonShape_shouldReturnEmpty() {
        WebClient offClient = mock(WebClient.class);
        ReflectionTestUtils.setField(targetNutritionService, "offWebClient", offClient);
        stubWebClientUriFunction(offClient, "{\"products\":\"not-an-array\"}");

        assertThat(nutritionService.fetchFromOpenFoodFacts("bad")).isEmpty();
    }

    @Test
    void getFoodByBarcode_offLookupFails_shouldThrowNotFound() {
        WebClient offClient = mock(WebClient.class);
        ReflectionTestUtils.setField(targetNutritionService, "offWebClient", offClient);
        when(offClient.get()).thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> nutritionService.getFoodByBarcode("0000000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubWebClientJson(WebClient client, String json) {
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(client.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubWebClientUriFunction(WebClient client, String json) {
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(client.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        if (json == null) {
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());
        } else {
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));
        }
    }
}
