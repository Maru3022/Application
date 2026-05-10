package com.healthlife.common.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppleOAuthRequest {
    @NotBlank
    private String identityToken;
    private String email;
    private String fullName;
}
