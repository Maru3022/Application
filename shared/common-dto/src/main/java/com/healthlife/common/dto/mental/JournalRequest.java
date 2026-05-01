package com.healthlife.common.dto.mental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalRequest {
    @NotBlank
    private String content;

    @NotNull private java.time.OffsetDateTime recordedAt;

    private java.util.List<String> tags;
}
