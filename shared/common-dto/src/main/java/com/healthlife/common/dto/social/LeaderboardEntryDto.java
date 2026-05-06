package com.healthlife.common.dto.social;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for a single leaderboard entry — avoids exposing the JPA entity directly. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDto {

    private UUID userId;
    private Integer progress;
}
