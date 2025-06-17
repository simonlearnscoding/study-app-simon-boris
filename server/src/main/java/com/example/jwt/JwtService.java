package com.example.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.access-expiration}")
    private long jwtExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private long refreshExpiration;

    private final TokenBlacklistService tokenBlacklistService;

    public JwtService(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    String getSecretKey() {
        return secretKey;
    }

    public long getAccessTokenExpirationTime() {
        return jwtExpiration;
    }

    public long getRefreshTokenExpirationTime() {
        return refreshExpiration;
    }

    void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    public void setJwtExpiration(long jwtExpiration) {
        this.jwtExpiration = jwtExpiration;
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        List<String> authorities =
                userDetails.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList();
        claims.put("authorities", authorities);
        System.out.println("Generating JWT for user: " + authorities);

        String token = buildToken(claims, userDetails, jwtExpiration);
        return token;
    }

    private String buildToken(
            Map<String, Object> extraClaims, CustomUserDetails userDetails, long expiration) {
        try {
            Key signingKey = getSigningKey();
            System.out.println("Using signing key: " + userDetails.getUsername());
            String token =
                    Jwts.builder()
                            .setClaims(extraClaims)
                            .setSubject(userDetails.getUsername())
                            .setIssuedAt(new Date(System.currentTimeMillis()))
                            .setExpiration(new Date(System.currentTimeMillis() + expiration))
                            .signWith(signingKey, SignatureAlgorithm.HS256)
                            .compact();

            return token;
        } catch (Exception e) {
            System.err.println("Token generation failed!");
            e.printStackTrace();
            throw new RuntimeException("JWT generation failed", e);
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractEmail(token);
        System.out.println("Validating token for user: " + username);
        System.out.println("Validating equality with: " + userDetails.getUsername());
        System.out.println("is token expired? " + isTokenExpired(token));
        System.out.println(
                "is token blacklisted? " + tokenBlacklistService.isTokenBlacklisted(token));
        return (username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && !tokenBlacklistService.isTokenBlacklisted(token)); // Missing this check!
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
