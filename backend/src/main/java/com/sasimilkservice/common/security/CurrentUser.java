package com.sasimilkservice.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Read-only accessor for the authenticated {@link AppUserPrincipal}. Used by
 * services that need the acting user id (auditing) without taking a Spring
 * Security dependency in their signatures.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AppUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static Optional<Long> id() {
        return principal().map(AppUserPrincipal::userId);
    }

    public static Optional<String> username() {
        return principal().map(AppUserPrincipal::username);
    }

    public static boolean has(String permission) {
        return principal()
                .map(p -> p.permissions().contains(permission))
                .orElse(false);
    }
}
