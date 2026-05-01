package com.healthlife.common.dto.health;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterRequest {
    @NotNull @Min(1)
    private Integer amountMl;

    @NotNull private java.time.OffsetDateTime recordedAt;
}
