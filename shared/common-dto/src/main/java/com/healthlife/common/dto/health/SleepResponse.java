package com.healthlife.common.dto.health;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SleepResponse {
    private UUID id;
    private OffsetDateTime sleepStart;
    private OffsetDateTime sleepEnd;
    private Integer durationMin;
    private Integer quality;
    private String notes;
    private String source;
    private OffsetDateTime createdAt;
}
