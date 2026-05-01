package com.healthlife.common.dto.mental;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodRequest {
    @NotNull @Min(1) @Max(10)
    private Integer moodScore;
    private List<String> emotions;
    private String note;
    @NotNull
    private OffsetDateTime recordedAt;
}
