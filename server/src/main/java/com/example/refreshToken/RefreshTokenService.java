package com.example.refreshToken;

import com.example.user.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final long refreshTokenDurationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${security.jwt.refresh-expiration}") long refreshTokenDurationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenDurationMs = refreshTokenDurationMs;
    }

    @Transactional
    public String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = passwordEncoder.encode(rawToken);

        refreshTokenRepository.revokeByUserAndDevice(user.getId(), deviceInfo);

        RefreshToken refreshToken =
                new RefreshToken(
                        user,
                        hashedToken,
                        deviceInfo,
                        ipAddress,
                        Instant.now().plusMillis(refreshTokenDurationMs));

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public String rotateRefreshToken(String rawOldToken, String deviceInfo, String ipAddress) {
        String hashedOldToken = passwordEncoder.encode(rawOldToken);
        RefreshToken oldToken =
                refreshTokenRepository
                        .findByTokenHash(hashedOldToken)
                        .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (oldToken.isRevoked()) {
            throw new InvalidTokenException("Refresh token was revoked");
        }

        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token expired");
        }

        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        return createRefreshToken(oldToken.getUser(), deviceInfo, ipAddress);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository
                .findByTokenHash(passwordEncoder.encode(rawToken))
                .ifPresent(
                        token -> {
                            token.setRevoked(true);
                            refreshTokenRepository.save(token);
                        });
    }

    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllByUser(userId);
    }

    public boolean isValidRefreshToken(String rawToken) {
        return refreshTokenRepository
                .findByTokenHash(passwordEncoder.encode(rawToken))
                .map(token -> !token.isRevoked() && token.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(Instant.now());
    }
}
