package com.healthlife.common.dto.health;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleRequest {
    @NotNull private LocalDate periodStart;

    private LocalDate periodEnd;
    private Integer cycleLength;
    private String flowIntensity;
    private java.util.List<String> symptoms;
    private String notes;
}
