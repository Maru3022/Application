package com.healthlife.common.dto.nutrition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class FoodLogRequest {
    @NotNull private UUID foodId;

    @NotNull @Positive private Double weightGrams;

    private OffsetDateTime consumedAt;
    private String mealType;
}
