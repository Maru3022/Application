package com.healthlife.common.dto.social;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for a friendship record — avoids exposing the JPA entity directly. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendDto {

    private UUID friendId;
    private String status;
    private OffsetDateTime since;
}
