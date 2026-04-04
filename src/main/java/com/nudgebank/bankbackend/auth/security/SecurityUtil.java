package com.nudgebank.bankbackend.auth.security;

import org.springframework.security.core.Authentication;

public final class SecurityUtil {
    private SecurityUtil() {
    }

    public static Long extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String str) {
            try {
                return Long.valueOf(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}