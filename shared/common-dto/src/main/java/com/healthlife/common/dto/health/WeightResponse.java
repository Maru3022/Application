package com.healthlife.common.dto.health;

import java.math.BigDecimal;
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
public class WeightResponse {
    private UUID id;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPct;
    private OffsetDateTime recordedAt;
    private OffsetDateTime createdAt;
}
