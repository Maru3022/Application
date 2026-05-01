package com.healthlife.common.dto.nutrition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFoodRequest {
    @NotBlank
    private String name;
    @Positive
    private Double caloriesPer100g;
    @Positive
    private Double proteinPer100g;
    @Positive
    private Double carbsPer100g;
    @Positive
    private Double fatPer100g;
}
