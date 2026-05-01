package com.healthlife.common.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 100)
    private String displayName;
    @Size(max = 50)
    private String timezone;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal heightCm;
}
