package com.example.config;

import com.example.jwt.JwtService;
import com.example.refreshToken.InvalidTokenException;
import com.example.refreshToken.RefreshTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver exceptionResolver;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            RefreshTokenService refreshTokenService,
            JwtService jwtService,
            UserDetailsService userDetailsService) {
        this.exceptionResolver = exceptionResolver;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Define public endpoints that truly don't need JWT verification
        return Arrays.asList(
                        "/auth/login",
                        "/auth/register",
                        "/auth/forgot-password",
                        "/auth/reset-password")
                .contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("\n=== REQUEST INSPECTION ===");
        System.out.println("Request Class: " + request.getClass().getName());
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Request URL: " + request.getRequestURL());
        System.out.println("Method: " + request.getMethod());
        System.out.println("Headers: ");
        Collections.list(request.getHeaderNames())
                .forEach(
                        header ->
                                System.out.println(
                                        "  " + header + ": " + request.getHeader(header)));

        try {
            if (isRefreshTokenRequest(request)) {
                System.out.println("🔄 Refresh token request detected");
                handleRefreshToken(request, response, filterChain);
                return;
            }
            // 1. Extract JWT
            final String jwt = extractJwtFromRequest(request);
            System.out.println("Extracted JWT: " + (jwt != null ? "[exists]" : "null"));

            // 🔁 No JWT provided → skip authentication
            if (jwt == null) {
                System.out.println("No JWT found - continuing chain");
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Validate and extract email
            final String username = jwtService.extractEmail(jwt);
            System.out.println("Decoded user email from JWT: " + username);

            // 🔁 Token invalid or already authenticated
            if (username == null) {
                System.out.println("⚠️ Invalid JWT - could not extract username");
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                System.out.println("ℹ️ User already authenticated - skipping");
                filterChain.doFilter(request, response);
                return;
            }

            // 3. Authenticate user
            System.out.println("Authenticating user: " + username);
            boolean isAuthenticated = authenticateUser(request, jwt, username);

            if (!isAuthenticated) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return; // Actually stop processing
            }
            System.out.println("✅ Authentication successful for: " + username);
            // In doFilterInternal(), after successful authentication:
            System.out.println("=== SECURITY CONTEXT DEBUG ===");
            System.out.println(
                    "Principal: "
                            + SecurityContextHolder.getContext()
                                    .getAuthentication()
                                    .getPrincipal());
            System.out.println(
                    "Authorities: "
                            + SecurityContextHolder.getContext()
                                    .getAuthentication()
                                    .getAuthorities());
            System.out.println(
                    "Details: "
                            + SecurityContextHolder.getContext().getAuthentication().getDetails());
            System.out.println(
                    "isAuthenticated: "
                            + SecurityContextHolder.getContext()
                                    .getAuthentication()
                                    .isAuthenticated());
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            System.err.println("⛔ Filter exception: " + e.getClass().getSimpleName());
            System.err.println("Error message: " + e.getMessage());
            exceptionResolver.resolveException(request, response, null, e);
        }
    }

    private void handleRefreshToken(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Extract refresh token from header
            String refreshToken = request.getHeader("Refresh-Token");

            // 2. Validate token existence
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new InvalidTokenException("Missing refresh token");
            }

            // 3. Validate token against service
            if (!refreshTokenService.isValidRefreshToken(refreshToken)) {
                throw new InvalidTokenException("Invalid refresh token");
            }

            // 4. Continue the filter chain if valid
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // 5. Handle exceptions consistently with main filter
            exceptionResolver.resolveException(request, response, null, e);
        }
    }

    private boolean isRefreshTokenRequest(HttpServletRequest request) {
        return request.getRequestURI().equals("/auth/refresh")
                && request.getMethod().equals("POST");
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        // Authorization header or invalid format → return null
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        // Extract JWT from the Authorization header, without the "Bearer " prefix
        return authHeader.substring(7);
    }

    private boolean authenticateUser(HttpServletRequest request, String jwt, String username) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            System.out.println("Loaded user details for: " + username);

            if (!jwtService.isTokenValid(jwt, userDetails)) {
                System.err.println("⚠️ JWT is invalid or expired for user: " + username);
                return false;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
            System.out.println("User authenticated: " + userDetails.getUsername());
            return true;

        } catch (Exception e) {
            System.err.println("⛔ Authentication failed for user: " + username);
            return false;
        }
    }
}
