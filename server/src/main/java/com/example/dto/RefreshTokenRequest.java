package com.example.dto;

public record RefreshTokenRequest(String token) {
    public String getToken() {
        return token;
    }
}
