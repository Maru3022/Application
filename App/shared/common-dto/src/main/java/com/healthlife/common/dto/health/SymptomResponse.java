package com.healthlife.common.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomResponse {
    private UUID id;
    private String symptom;
    private Integer intensity;
    private OffsetDateTime recordedAt;
    private String notes;
}
