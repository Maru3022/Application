package com.healthlife.common.dto.aicoach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightDto {
    private String type;
    private String content;
    private java.time.LocalDateTime generatedAt;
}
