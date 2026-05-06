package com.healthlife.common.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerifyRequest {

    /**
     * The user's email address. Must be supplied in the request body — never via a
     * client-controlled header — to prevent identity spoofing during MFA verification.
     */
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code;
}
