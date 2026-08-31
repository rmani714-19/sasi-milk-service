package com.sasimilkservice.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.crypto.*}. {@code fieldKey} is a base64-encoded 32-byte key for
 * AES-256-GCM encryption of producer bank-account numbers at rest
 * (spec producers.rules: "Bank account number must be stored securely").
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String fieldKey) {
}
