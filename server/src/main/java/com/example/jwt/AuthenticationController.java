package com.example.jwt;

import com.example.dto.LoginUserDTO;
import com.example.dto.RefreshTokenRequest;
import com.example.dto.RegisterUserDTO;
import com.example.dto.VerifyUserDTO;
import com.example.refreshToken.InvalidTokenException;
import com.example.refreshToken.RefreshTokenService;
import com.example.responses.LoginResponse;
import com.example.user.User;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;

    public AuthenticationController(
            JwtService jwtService,
            AuthenticationService authenticationService,
            TokenBlacklistService tokenBlacklistService,
            UserDetailsService userDetailsService,
            RefreshTokenService refreshTokenService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationService = authenticationService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshTokens(
            @RequestBody RefreshTokenRequest refreshRequest) {
        // 1. Validate refresh token
        if (!refreshTokenService.isValidRefreshToken(refreshRequest.getToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        // 2. Extract username from refresh token
        String email = jwtService.extractEmail(refreshRequest.getToken());
        UserDetails userDetails = CustomUserDetailsService.loadUserByUsername(email);

        // 3. Generate new tokens (with rotation)
        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken =
                refreshTokenService.rotateRefreshToken(
                        refreshRequest.getToken(),
                        "web", // or get from request
                        request.getRemoteAddr());

        // 4. Return new tokens
        return ResponseEntity.ok(
                new LoginResponse(
                        newAccessToken,
                        newRefreshToken,
                        jwtService.getAccessTokenExpirationTime(),
                        jwtService.getRefreshTokenExpirationTime()));
    }

    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody RegisterUserDTO input) {
        User registeredUser = authenticationService.signup(input);
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDTO loginUserDTO) {
        CustomUserDetails authenticatedUser = authenticationService.authenticate(loginUserDTO);
        String token = jwtService.generateToken(authenticatedUser);
        String refreshToken = jwtService.generateRefreshToken(authenticatedUser);
        LoginResponse loginResponse =
                new LoginResponse(
                        token,
                        refreshToken,
                        jwtService.getAccessTokenExpirationTime(),
                        jwtService.getRefreshTokenExpirationTime());
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody RefreshTokenRequest refreshTokenRequest) {

        String accessToken = authHeader.substring(7); // Remove "Bearer "
        tokenBlacklistService.blacklistToken(accessToken);
        refreshTokenService.revokeRefreshToken(refreshTokenRequest.getToken());

        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDTO verifyUserDTO) {
        try {
            authenticationService.verifyUser(verifyUserDTO);
            return ResponseEntity.ok("User verified successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        try {
            authenticationService.resendVerificationCode(email);
            return ResponseEntity.ok("Verification email resent successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
