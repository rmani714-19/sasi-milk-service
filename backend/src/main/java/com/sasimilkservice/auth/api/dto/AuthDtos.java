package com.sasimilkservice.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Auth request/response payloads. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record LogoutRequest(
            @NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            boolean mustChangePassword) {

        public static TokenResponse bearer(String access, String refresh, long ttl, boolean mustChange) {
            return new TokenResponse(access, refresh, "Bearer", ttl, mustChange);
        }
    }
}
