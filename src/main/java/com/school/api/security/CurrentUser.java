package com.school.api.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves the id of the caller. Bearer-token requests carry it in the {@code user_id}
 * claim; direct authentication results (login flow) expose the loaded account. Returns
 * {@code null} when the caller cannot be resolved, so audit columns degrade gracefully.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID idOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user.getAccount().getId();
        }
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");
            return userId == null ? null : UUID.fromString(userId);
        }
        return null;
    }
}
