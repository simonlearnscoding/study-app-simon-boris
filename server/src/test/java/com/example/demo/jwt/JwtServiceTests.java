package com.example.demo.jwt;

import static org.junit.jupiter.api.Assertions.*;

import com.example.jwt.JwtService;
import com.example.jwt.TokenBlacklistService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {
    @Mock private TokenBlacklistService tokenBlacklistService;

    private JwtService jwtService;
    private final String SECRET_KEY =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long EXPIRATION_TIME = 86400000; // 24 hours

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(tokenBlacklistService);
        // Use the package-private setters

        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION_TIME);
    }

    @Test
    void testExtractUsername() {
        String token =
                jwtService.generateToken(new User("testuser", "password", Collections.emptyList()));
        assertEquals("testuser", jwtService.extractEmail(token));
    }

    @Test
    void testGenerateToken() {
        String token =
                jwtService.generateToken(new User("testuser", "password", Collections.emptyList()));
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void testTokenValidity() {
        UserDetails user = new User("testuser", "password", Collections.emptyList());
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void testTokenInvalidForWrongUser() {
        UserDetails user1 = new User("testuser", "password", Collections.emptyList());
        UserDetails user2 = new User("wronguser", "password", Collections.emptyList());
        String token = jwtService.generateToken(user1);
        assertFalse(jwtService.isTokenValid(token, user2));
    }

    @Test
    void testExpiredToken() {
        jwtService.setJwtExpiration(-1000); // Set to past
        String token =
                jwtService.generateToken(new User("testuser", "password", Collections.emptyList()));
        jwtService.setJwtExpiration(EXPIRATION_TIME); // Reset

        assertThrows(
                ExpiredJwtException.class,
                () ->
                        jwtService.isTokenValid(
                                token, new User("testuser", "password", Collections.emptyList())));
    }

    @Test
    void testTokenWithAuthorities() {
        UserDetails user =
                new User("testuser", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String token = jwtService.generateToken(user);
        Claims claims = jwtService.extractAllClaims(token);
        assertNotNull(claims.get("authorities"));
    }

    // ... other test methods ...
}
