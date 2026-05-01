package com.healthlife.common.dto.mental;

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
public class StressResponse {
    private UUID id;
    private Integer level;
    private OffsetDateTime recordedAt;
    private String notes;
}
