package com.healthlife.common.dto.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeResponse {
    private UUID id;
    private String title;
    private String description;
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer targetValue;
    private Integer participantCount;
    private boolean joined;
}
