package com.healthlife.common.dto.mental;

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
public class BreathingSessionRequest {
    @NotNull
    private String technique;
    @NotNull @Min(1)
    private Integer durationMin;
    private java.time.OffsetDateTime recordedAt;
}
