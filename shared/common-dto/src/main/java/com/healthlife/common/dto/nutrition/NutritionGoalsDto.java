package com.healthlife.common.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Daily nutrition targets for a user. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionGoalsDto {

    private Double dailyCalories;
    private Double dailyProteinG;
    private Double dailyCarbsG;
    private Double dailyFatG;
}
