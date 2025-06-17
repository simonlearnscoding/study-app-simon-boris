package com.example.demo.jwt;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.dto.LoginUserDTO;
import com.example.dto.RefreshTokenRequest;
import com.example.dto.RegisterUserDTO;
import com.example.jwt.AuthenticationController;
import com.example.jwt.AuthenticationService;
import com.example.jwt.JwtService;
import com.example.jwt.TokenBlacklistService;
import com.example.refreshToken.RefreshTokenService;
import com.example.user.User;
import com.example.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JwtService jwtService;

    @Mock private AuthenticationService authenticationService;

    @Mock private TokenBlacklistService tokenBlacklistService;

    @Mock private RefreshTokenService refreshTokenService;

    @Mock private UserDetailsService userDetailsService;

    @Mock private AuthenticationManager authenticationManager;

    @Mock private UserService userService;

    @InjectMocks private AuthenticationController authenticationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authenticationController).build();
    }

    @Test
    void login_ShouldReturnTokens() throws Exception {
        // Arrange
        LoginUserDTO loginDto = new LoginUserDTO("test@example.com", "password");
        User authenticatedUser = new User();
        authenticatedUser.setEmail("test@example.com");
        authenticatedUser.setEnabled(true);

        when(authenticationService.authenticate(any(LoginUserDTO.class)))
                .thenReturn(authenticatedUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("mock-refresh-token");
        when(jwtService.getAccessTokenExpirationTime()).thenReturn(86400000L);
        when(jwtService.getRefreshTokenExpirationTime()).thenReturn(604800000L);

        // Act & Assert in single call
        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(86400000L))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(604800000L));

        // Verify
        verify(authenticationService, times(1)).authenticate(any(LoginUserDTO.class));
        verify(jwtService).generateToken(any(User.class));
        verify(jwtService).generateRefreshToken(any(User.class));
    }

    @Test
    void refresh_ShouldReturnNewTokens() throws Exception {
        // Arrange
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("refresh-token");
        UserDetails userDetails =
                org.springframework.security.core.userdetails.User.withUsername("testuser")
                        .password("password")
                        .roles("USER")
                        .build();

        when(refreshTokenService.isValidRefreshToken(anyString())).thenReturn(true);
        when(jwtService.extractEmail(anyString())).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationTime()).thenReturn(86400000L);
        when(jwtService.getRefreshTokenExpirationTime()).thenReturn(604800000L);
        when(refreshTokenService.rotateRefreshToken(anyString(), anyString(), anyString()))
                .thenReturn("new-refresh-token");

        // Act & Assert
        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void logout_ShouldBlacklistTokens() throws Exception {
        // Arrange
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("refresh-token");

        // Act & Assert
        mockMvc.perform(
                        post("/auth/logout")
                                .header("Authorization", "Bearer access-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged out successfully"));

        verify(tokenBlacklistService).blacklistToken("access-token");
        verify(refreshTokenService).revokeRefreshToken("refresh-token");
    }

    @Test
    void signup_ShouldRegisterUser() throws Exception {
        // Arrange
        RegisterUserDTO registerDto =
                new RegisterUserDTO("newuser", "email@example.com", "password", "newUser");
        User registeredUser = new User();
        registeredUser.setUsername("newuser");

        when(authenticationService.signup(any(RegisterUserDTO.class))).thenReturn(registeredUser);

        // Act & Assert
        mockMvc.perform(
                        post("/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"));
    }
}
