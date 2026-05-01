package com.healthlife.common.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}
