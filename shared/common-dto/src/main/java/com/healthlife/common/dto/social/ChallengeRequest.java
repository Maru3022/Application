package com.healthlife.common.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeRequest {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private String type;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    private Integer targetValue;
}
