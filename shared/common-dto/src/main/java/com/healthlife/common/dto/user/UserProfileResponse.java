package com.healthlife.common.dto.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String displayName;
    private String timezone;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal heightCm;
    private String avatarUrl;
    private String subscriptionPlan;
}
