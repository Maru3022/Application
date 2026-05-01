package com.healthlife.common.dto.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomRequest {
    @NotBlank
    private String symptom;
    @Min(1) @Max(10)
    private Integer intensity;
    @NotNull
    private java.time.OffsetDateTime recordedAt;
    private String notes;
}
