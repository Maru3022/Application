package com.healthlife.common.dto.user;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
