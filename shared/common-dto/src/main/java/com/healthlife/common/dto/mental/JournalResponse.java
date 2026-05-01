package com.healthlife.common.dto.mental;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalResponse {
    private UUID id;
    private String content;
    private List<String> tags;
    private OffsetDateTime recordedAt;
    private OffsetDateTime createdAt;
}
