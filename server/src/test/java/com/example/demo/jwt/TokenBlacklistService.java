package com.example.demo.jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.jwt.TokenBlacklistService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private static final long JWT_EXPIRATION_MS = 86400000; // 24 hours

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenBlacklistService = new TokenBlacklistService(redisTemplate, JWT_EXPIRATION_MS);
    }

    @Test
    void blacklistToken_ShouldSetTokenInRedis() {
        String token = "test-token";
        tokenBlacklistService.blacklistToken(token);

        verify(valueOperations)
                .set(
                        eq("blacklist:" + token),
                        eq("revoked"),
                        eq(Duration.ofMillis(JWT_EXPIRATION_MS)));
    }

    @Test
    void isTokenBlacklisted_ShouldReturnTrueWhenTokenExists() {
        String token = "test-token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(true);

        assertTrue(tokenBlacklistService.isTokenBlacklisted(token));
    }

    @Test
    void isTokenBlacklisted_ShouldReturnFalseWhenTokenNotExists() {
        String token = "test-token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(false);

        assertFalse(tokenBlacklistService.isTokenBlacklisted(token));
    }
}
