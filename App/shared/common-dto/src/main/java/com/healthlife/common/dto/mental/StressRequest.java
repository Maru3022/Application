package com.healthlife.common.dto.mental;

import jakarta.validation.constraints.Max;
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
public class StressRequest {
    @NotNull @Min(1) @Max(10)
    private Integer level;
    @NotNull
    private java.time.OffsetDateTime recordedAt;
    private String notes;
}
