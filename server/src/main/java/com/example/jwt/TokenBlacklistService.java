package com.example.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private final long jwtExpirationMs;

    public TokenBlacklistService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${security.jwt.access-expiration}") long jwtExpirationMs) {
        this.redisTemplate = redisTemplate;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public void blacklistToken(String token) {
        redisTemplate
                .opsForValue()
                .set("blacklist:" + token, "revoked", Duration.ofMillis(jwtExpirationMs));
    }

    public boolean isTokenBlacklisted(String token) {
        System.out.println("Checking blacklist for token: " + token);
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }
}
