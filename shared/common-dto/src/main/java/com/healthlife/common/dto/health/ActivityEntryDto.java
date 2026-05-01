package com.healthlife.common.dto.health;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEntryDto {
    private UUID id;
    private LocalDate date;
    private Integer steps;
    private Integer caloriesBurned;
    private Integer activeMinutes;
    private Integer distanceM;
    private String source;
}
