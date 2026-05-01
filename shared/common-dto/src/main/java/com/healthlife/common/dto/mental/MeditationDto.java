package com.healthlife.common.dto.mental;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeditationDto {
    private UUID id;
    private String title;
    private String description;
    private Integer durationMin;
    private String category;
    private String audioUrl;
}
