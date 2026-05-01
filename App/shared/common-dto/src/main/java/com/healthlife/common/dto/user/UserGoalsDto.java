package com.healthlife.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGoalsDto {
    private Integer dailySteps;
    private Integer sleepMinutes;
    private Integer waterMl;
    private BigDecimal targetWeightKg;
}
