package com.healthlife.common.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated health dashboard data for the current user. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {

    /** Total water consumed today in millilitres. */
    private int waterTodayMl;

    /** Steps recorded today (null if no activity entry exists). */
    private Integer stepsTodayCount;

    /** Duration of the most recent sleep entry in minutes (null if none). */
    private Integer lastSleepDurationMin;

    /** Quality score of the most recent sleep entry (null if none). */
    private Integer lastSleepQuality;

    /** Most recent body weight in kg (null if no weight entry exists). */
    private Double latestWeightKg;
}
