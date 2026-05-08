package com.healthlife.payment.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {

    private String plan;
    private String status;
    private OffsetDateTime currentPeriodEnd;
    private OffsetDateTime canceledAt;
}
