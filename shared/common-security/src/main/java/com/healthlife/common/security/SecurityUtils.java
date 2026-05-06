package com.healthlife.common.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID) {
            return (UUID) auth.getPrincipal();
        }
        throw new com.healthlife.common.exception.UnauthorizedException("User not authenticated");
    }

    /**
     * Returns the email of the currently authenticated user, extracted from the JWT credentials
     * stored in the {@link org.springframework.security.core.context.SecurityContext}.
     */
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String email) {
            return email;
        }
        throw new com.healthlife.common.exception.UnauthorizedException("User not authenticated");
    }
}
