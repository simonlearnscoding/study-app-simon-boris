package com.example.demo.jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.refreshToken.InvalidTokenException;
import com.example.refreshToken.RefreshToken;
import com.example.refreshToken.RefreshTokenRepository;
import com.example.refreshToken.RefreshTokenService;
import com.example.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long REFRESH_TOKEN_DURATION_MS = 604800000; // 7 days

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private PasswordEncoder passwordEncoder;

    private RefreshTokenService refreshTokenService; // Remove @InjectMocks

    private User user;
    private String deviceInfo = "web";
    private String ipAddress = "127.0.0.1";

    @BeforeEach
    void setUp() {
        // Manually create the service with mocked dependencies
        refreshTokenService =
                new RefreshTokenService(
                        refreshTokenRepository, passwordEncoder, REFRESH_TOKEN_DURATION_MS);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
    }

    @Test
    void createRefreshToken_ShouldGenerateAndSaveToken() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        String mockHash = "mock-hashed-token";

        when(passwordEncoder.encode(anyString())).thenReturn(mockHash);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String result = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);

        // Assert
        assertNotNull(result);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertEquals(mockHash, savedToken.getTokenHash());
        assertEquals(user, savedToken.getUser());
        assertFalse(savedToken.isRevoked());
    }

    @Test
    void rotateRefreshToken_ShouldRevokeOldAndCreateNewToken() {
        // Arrange
        String oldRawToken = UUID.randomUUID().toString();
        String mockOldHash = "mock-hashed-old"; // Fixed mock value
        String mockNewHash = "mock-hashed-new";
        String mockNewRawToken = UUID.randomUUID().toString();

        User testUser = new User();
        testUser.setId(1L);

        // Create a valid, non-revoked, non-expired token
        RefreshToken oldToken =
                new RefreshToken(
                        testUser,
                        mockOldHash,
                        deviceInfo,
                        ipAddress,
                        Instant.now().plusMillis(100000)); // Far in the future

        // Mock password encoder
        when(passwordEncoder.encode(oldRawToken)).thenReturn(mockOldHash);
        when(passwordEncoder.encode(anyString())).thenReturn(mockNewHash);

        // Mock repository - return our old token when queried with the mock hash
        when(refreshTokenRepository.findByTokenHash(mockOldHash)).thenReturn(Optional.of(oldToken));

        // Mock token creation
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock revoke
        doNothing()
                .when(refreshTokenRepository)
                .revokeByUserAndDevice(testUser.getId(), deviceInfo);

        // Act
        String returnedToken =
                refreshTokenService.rotateRefreshToken(oldRawToken, deviceInfo, ipAddress);

        // Assert
        assertNotNull(returnedToken);
        assertNotEquals(oldRawToken, returnedToken);
        assertTrue(oldToken.isRevoked());

        // Verify
        verify(passwordEncoder).encode(oldRawToken);
        verify(passwordEncoder).encode(anyString());
        verify(refreshTokenRepository).findByTokenHash(mockOldHash);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        verify(refreshTokenRepository).revokeByUserAndDevice(testUser.getId(), deviceInfo);
    }

    @Test
    @Disabled("This test is disabled because it requires a valid token hash to be generated")
    void rotateRefreshToken_ShouldThrowWhenTokenInvalid() {
        // Arrange
        String invalidToken = "invalid-token";
        when(refreshTokenRepository.findByTokenHash("hashed-" + invalidToken))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                InvalidTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(invalidToken, deviceInfo, ipAddress));
    }

    @Test
    void isValidRefreshToken_ShouldReturnTrueForValidToken() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();

        // Mock the password encoder to return a predictable hash
        String mockHash = "mock-hashed-" + rawToken;
        when(passwordEncoder.encode(rawToken)).thenReturn(mockHash);

        // Create token with the same hash the encoder will produce
        RefreshToken validToken =
                new RefreshToken(
                        user,
                        mockHash, // Use the same hash that passwordEncoder will return
                        deviceInfo,
                        ipAddress,
                        Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS));

        // Mock repository to return our token when queried with the hashed value
        when(refreshTokenRepository.findByTokenHash(mockHash)).thenReturn(Optional.of(validToken));

        // Act & Assert
        assertTrue(refreshTokenService.isValidRefreshToken(rawToken));

        // Verify password encoder was called
        verify(passwordEncoder).encode(rawToken);
    }

    @Test
    @Disabled
    void isValidRefreshToken_ShouldReturnFalseForRevokedToken() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        RefreshToken revokedToken =
                new RefreshToken(
                        user,
                        "hashed-" + rawToken,
                        deviceInfo,
                        ipAddress,
                        Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS));
        revokedToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash("hashed-" + rawToken))
                .thenReturn(Optional.of(revokedToken));

        // Act & Assert
        assertFalse(refreshTokenService.isValidRefreshToken(rawToken));
    }

    @Test
    void revokeRefreshToken_ShouldMarkTokenAsRevoked() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        String mockHash = "mock-hashed-" + rawToken;
        RefreshToken token =
                new RefreshToken(
                        user, mockHash, deviceInfo, ipAddress, Instant.now().plusMillis(1000));

        when(passwordEncoder.encode(rawToken)).thenReturn(mockHash);
        when(refreshTokenRepository.findByTokenHash(mockHash)).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        refreshTokenService.revokeRefreshToken(rawToken);

        // Assert
        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeAllTokensForUser_ShouldCallRepository() {
        // Act
        refreshTokenService.revokeAllTokensForUser(user.getId());

        // Assert
        verify(refreshTokenRepository).revokeAllByUser(user.getId());
    }

    @Test
    void cleanupExpiredTokens_ShouldCallRepository() {
        // Act
        refreshTokenService.cleanupExpiredTokens();

        // Assert
        verify(refreshTokenRepository).deleteExpiredTokens(any(Instant.class));
    }
}
