package com.healthlife.common.dto.user;

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
public class ChangePasswordRequest {
    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}
