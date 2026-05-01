package com.healthlife.common.dto.social;

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
public class PostResponse {
    private UUID id;
    private UUID userId;
    private String displayName;
    private String content;
    private String type;
    private Integer likesCount;
    private OffsetDateTime createdAt;
}
