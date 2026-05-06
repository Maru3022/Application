package com.healthlife.common.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated sleep statistics for a user over a rolling window. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SleepStatsDto {

    /** Number of sleep entries analysed. */
    private int entryCount;

    /** Average sleep duration in minutes. */
    private double avgDurationMin;

    /** Average sleep quality score (1–10). Null if no quality data recorded. */
    private Double avgQuality;

    /** Minimum sleep duration in minutes across the window. */
    private int minDurationMin;

    /** Maximum sleep duration in minutes across the window. */
    private int maxDurationMin;

    /** Percentage of nights where sleep duration met the 7-hour goal (420 min). */
    private double goalAchievementPct;
}
