package com.healthlife.common.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated nutrition totals for a given day. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionAnalysisDto {

    private double totalCalories;
    private double totalProteinG;
    private double totalCarbsG;
    private double totalFatG;

    /** Number of food log entries included in this analysis. */
    private int entryCount;
}
