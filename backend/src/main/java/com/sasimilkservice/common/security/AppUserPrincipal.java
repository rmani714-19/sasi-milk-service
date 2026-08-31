package com.sasimilkservice.common.security;

import java.util.Set;

/**
 * Authenticated principal placed in the {@code SecurityContext} by
 * {@link JwtAuthenticationFilter}. Carries only what the app needs downstream:
 * the user id (for auditing), the username, and the resolved permission codes.
 */
public record AppUserPrincipal(Long userId, String username, Set<String> permissions) {
}
