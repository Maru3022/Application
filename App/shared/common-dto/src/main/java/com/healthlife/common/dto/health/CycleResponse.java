package com.healthlife.common.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleResponse {
    private UUID id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer cycleLength;
    private String flowIntensity;
    private List<String> symptoms;
    private String notes;
}
