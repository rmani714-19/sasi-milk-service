package com.sasimilkservice.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds {@code app.security.jwt.*}. The secret and TTLs come from configuration
 * (AWS Secrets Manager in real environments), never hard-coded.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
