package com.healthlife.common.dto.health;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightRequest {
    @NotNull
    private BigDecimal weightKg;
    private BigDecimal bodyFatPct;
    @NotNull
    private java.time.OffsetDateTime recordedAt;
}
