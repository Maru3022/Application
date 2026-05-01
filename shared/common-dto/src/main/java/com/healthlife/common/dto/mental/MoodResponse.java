package com.healthlife.common.dto.mental;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodResponse {
    private UUID id;
    private Integer moodScore;
    private List<String> emotions;
    private String note;
    private OffsetDateTime recordedAt;
    private OffsetDateTime createdAt;
}
