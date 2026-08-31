package com.sasimilkservice.auth.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.auth.api.dto.AuthDtos.TokenResponse;
import com.sasimilkservice.auth.domain.RefreshToken;
import com.sasimilkservice.auth.repository.RefreshTokenRepository;
import com.sasimilkservice.common.security.JwtProperties;
import com.sasimilkservice.common.security.JwtService;
import com.sasimilkservice.user.domain.UserAccount;
import com.sasimilkservice.user.repository.UserAccountRepository;
import com.sasimilkservice.user.service.PermissionResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/**
 * Login, refresh-token rotation and logout. Access tokens are stateless JWTs;
 * refresh tokens are opaque random strings stored hashed.
 */
@Service
public class AuthService {

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PermissionResolver permissionResolver;
    private final AuditRecorder audit;

    public AuthService(UserAccountRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PermissionResolver permissionResolver,
                       AuditRecorder audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.permissionResolver = permissionResolver;
        this.audit = audit;
    }

    @Transactional
    public TokenResponse login(String username, String rawPassword, String userAgent, String ip) {
        UserAccount user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (user.getStatus() != UserAccount.Status.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            throw new BadCredentialsException("Invalid username or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());

        TokenResponse response = issueTokens(user, userAgent, ip);
        audit.record("LOGIN", "User", user.getId(), "User " + user.getUsername() + " logged in");
        return response;
    }

    @Transactional
    public TokenResponse refresh(String presentedToken, String userAgent, String ip) {
        String hash = sha256(presentedToken);
        RefreshToken current = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!current.isActive(Instant.now())) {
            // token reuse / expiry — revoke the whole family defensively
            refreshTokens.revokeAllForUser(current.getUserId(), Instant.now());
            throw new BadCredentialsException("Refresh token is no longer valid");
        }

        UserAccount user = users.findById(current.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        current.setRevokedAt(Instant.now());
        TokenResponse response = issueTokens(user, userAgent, ip);
        // link rotation chain
        refreshTokens.findByTokenHash(sha256(response.refreshToken()))
                .ifPresent(next -> current.setReplacedById(next.getId()));
        return response;
    }

    @Transactional
    public void logout(String presentedToken) {
        refreshTokens.findByTokenHash(sha256(presentedToken))
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    private TokenResponse issueTokens(UserAccount user, String userAgent, String ip) {
        Set<String> perms = permissionResolver.effectivePermissions(user);
        String access = jwtService.issueAccessToken(user.getId(), user.getUsername(), perms);

        String rawRefresh = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(rawRefresh));
        rt.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ip);
        refreshTokens.saveAndFlush(rt);

        return TokenResponse.bearer(access, rawRefresh, jwtService.accessTokenTtlSeconds(),
                user.isMustChangePassword());
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
