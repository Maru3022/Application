package com.healthlife.common.dto.nutrition;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodLogResponse {
    private UUID id;
    private UUID foodId;
    private String foodName;
    private Double weightGrams;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private String mealType;
    private OffsetDateTime consumedAt;
}
