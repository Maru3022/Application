package com.healthlife.common.dto.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SleepRequest {
    @NotNull
    private OffsetDateTime sleepStart;
    @NotNull
    private OffsetDateTime sleepEnd;
    @Min(1) @Max(5)
    private Integer quality;
    private String notes;
    private String source;
}
