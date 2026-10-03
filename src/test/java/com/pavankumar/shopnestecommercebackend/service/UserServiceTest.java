package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.AuthResponse;
import com.pavankumar.shopnestecommercebackend.dto.ForgotPasswordRequest;
import com.pavankumar.shopnestecommercebackend.dto.LoginRequest;
import com.pavankumar.shopnestecommercebackend.dto.RefreshTokenRequest;
import com.pavankumar.shopnestecommercebackend.dto.RegisterRequest;
import com.pavankumar.shopnestecommercebackend.dto.ResetPasswordRequest;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceAlreadyExistsException;
import com.pavankumar.shopnestecommercebackend.model.AuthType;
import com.pavankumar.shopnestecommercebackend.model.PasswordResetToken;
import com.pavankumar.shopnestecommercebackend.model.RefreshToken;
import com.pavankumar.shopnestecommercebackend.model.Role;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.PasswordResetTokenRepository;
import com.pavankumar.shopnestecommercebackend.repository.RefreshTokenRepository;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.security.JwtUtil;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthUtil util;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserDetailsService userDetailsService;
    @Mock private Authentication authentication;
    @Mock private UserDetails userDetails;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "frontendUrl", "https://shopnest-test.local");
    }

    private User userWithAuthType(AuthType authType) {
        return User.builder()
                .id(1L)
                .name("Pavan")
                .email("pavan@test.com")
                .password("hashed-pw")
                .authType(authType)
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void register_emailAlreadyExists_throwsResourceAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Pavan");
        request.setEmail("pavan@test.com");
        request.setPassword("rawPassword123");

        when(userRepository.existsByEmail("pavan@test.com")).thenReturn(true);
        ResourceAlreadyExistsException thrown = assertThrows(ResourceAlreadyExistsException.class,
                () -> userService.register(request));

        assertEquals("Email already registered: pavan@test.com", thrown.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_newEmail_savesUserWithEncodedPasswordDefaultRoleAndLocalAuthType() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Pavan");
        request.setEmail("pavan@test.com");
        request.setPassword("rawPassword123");

        when(userRepository.existsByEmail("pavan@test.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword123")).thenReturn("encoded-pw-hash");
        AuthResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("Pavan", savedUser.getName());
        assertEquals("pavan@test.com", savedUser.getEmail());
        assertEquals("encoded-pw-hash", savedUser.getPassword());
        assertNotEquals("rawPassword123", savedUser.getPassword(),
                "raw password must never be persisted as-is");
        assertEquals(Role.ROLE_USER, savedUser.getRole());
        assertEquals(AuthType.LOCAL, savedUser.getAuthType());
        assertEquals("ROLE_USER", response.getRole());
        assertEquals("registration successfully", response.getMessage());
    }

    @Test
    void login_emailNotFound_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("missing@test.com", "anyPassword123");
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        BadCredentialsException thrown = assertThrows(BadCredentialsException.class,
                () -> userService.login(request));

        assertEquals("Invalid email or password", thrown.getMessage());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_googleOnlyAccount_throwsBadCredentialsExceptionWithGoogleMessage() {
        LoginRequest request = new LoginRequest("pavan@test.com", "anyPassword123");
        when(userRepository.findByEmail("pavan@test.com"))
                .thenReturn(Optional.of(userWithAuthType(AuthType.GOOGLE)));

        BadCredentialsException thrown = assertThrows(BadCredentialsException.class,
                () -> userService.login(request));

        assertEquals("This account can only be accessed using Google login", thrown.getMessage());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_localAccountWrongPassword_propagatesBadCredentialsExceptionFromAuthManager() {
        LoginRequest request = new LoginRequest("pavan@test.com", "wrongPassword123");
        when(userRepository.findByEmail("pavan@test.com"))
                .thenReturn(Optional.of(userWithAuthType(AuthType.LOCAL)));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> userService.login(request));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_localAccountCorrectPassword_returnsAuthResponseAndSavesRefreshToken() {
        LoginRequest request = new LoginRequest("pavan@test.com", "correctPassword123");
        User user = userWithAuthType(AuthType.LOCAL);

        when(userRepository.findByEmail("pavan@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails)).thenReturn("fake-jwt-token");
        when(jwtUtil.extractRoleFromUserDetails(userDetails)).thenReturn("ROLE_USER");

        AuthResponse response = userService.login(request);

        assertEquals("fake-jwt-token", response.getToken());
        assertEquals("ROLE_USER", response.getRole());
        assertEquals("login successful", response.getMessage());
        assertNotNull(response.getRefreshToken());
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(user, tokenCaptor.getValue().getUser());
    }

    @Test
    void forgotPassword_emailNotFound_doesNothingSilently() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@test.com");

        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.forgotPassword(request));
        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetTokenRepository, never()).deleteAllUnusedByUser(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_googleOnlyAccount_doesNothingSilently() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("pavan@test.com");

        when(userRepository.findByEmail("pavan@test.com"))
                .thenReturn(Optional.of(userWithAuthType(AuthType.GOOGLE)));

        assertDoesNotThrow(() -> userService.forgotPassword(request));
        verify(passwordResetTokenRepository, never()).deleteAllUnusedByUser(any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_localAccount_clearsOldTokensSavesNewTokenAndSendsEmailWithRawNotHashedToken() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("pavan@test.com");
        User user = userWithAuthType(AuthType.LOCAL);

        when(userRepository.findByEmail("pavan@test.com")).thenReturn(Optional.of(user));
        userService.forgotPassword(request);

        verify(passwordResetTokenRepository).deleteAllUnusedByUser(user);
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertFalse(savedToken.isUsed());
        assertNotNull(savedToken.getExpiresAt());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("pavan@test.com"), eq("Pavan"), linkCaptor.capture());
        String resetLink = linkCaptor.getValue();
        assertTrue(resetLink.startsWith("https://shopnest-test.local/reset-password?token="));

        String rawTokenFromLink = resetLink.substring(resetLink.indexOf("token=") + "token=".length());
        assertNotEquals(savedToken.getTokenHash(), rawTokenFromLink,
                "the raw token emailed to the user must never equal the hash stored in the database");
    }

    private PasswordResetToken buildResetToken(User user, boolean used, LocalDateTime expiresAt) {
        return PasswordResetToken.builder()
                .user(user)
                .tokenHash("irrelevant-hash-value")
                .used(used)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void resetPassword_tokenNotFound_throwsBadRequestException() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "newPassword123");
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> userService.resetPassword(request));

        assertEquals("Invalid or expired reset link", thrown.getMessage());
    }

    @Test
    void resetPassword_tokenAlreadyUsed_throwsBadRequestExceptionWithSameGenericMessage() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "newPassword123");
        User user = userWithAuthType(AuthType.LOCAL);
        PasswordResetToken usedToken = buildResetToken(user, true, LocalDateTime.now().plusMinutes(10));

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usedToken));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> userService.resetPassword(request));

        assertEquals("Invalid or expired reset link", thrown.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_tokenExpired_throwsBadRequestExceptionWithSameGenericMessage() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "newPassword123");
        User user = userWithAuthType(AuthType.LOCAL);
        PasswordResetToken expiredToken = buildResetToken(user, false, LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> userService.resetPassword(request));

        assertEquals("Invalid or expired reset link", thrown.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_validToken_updatesPasswordDeletesRefreshTokensAndMarksTokenUsed() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "newPassword123");
        User user = userWithAuthType(AuthType.LOCAL);
        PasswordResetToken validToken = buildResetToken(user, false, LocalDateTime.now().plusMinutes(10));

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-encoded-pw");
        userService.resetPassword(request);

        assertEquals("new-encoded-pw", user.getPassword());
        verify(userRepository).save(user);
        verify(refreshTokenRepository).deleteAllByUser(user);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertTrue(tokenCaptor.getValue().isUsed());
    }

    @Test
    void refreshAccessToken_tokenNotFound_throwsBadCredentialsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("raw-refresh-token");
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        BadCredentialsException thrown = assertThrows(BadCredentialsException.class,
                () -> userService.refreshAccessToken(request));

        assertEquals("Invalid refresh token", thrown.getMessage());
    }

    @Test
    void refreshAccessToken_tokenExpired_throwsBadCredentialsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("raw-refresh-token");
        User user = userWithAuthType(AuthType.LOCAL);
        RefreshToken expiredToken = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant-hash")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        BadCredentialsException thrown = assertThrows(BadCredentialsException.class,
                () -> userService.refreshAccessToken(request));

        assertEquals("Refresh token expired, please login again", thrown.getMessage());
    }

    @Test
    void refreshAccessToken_validToken_returnsNewAccessTokenButEchoesSameRefreshTokenUnchanged() {
        String rawRefreshToken = "raw-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawRefreshToken);
        User user = userWithAuthType(AuthType.LOCAL);
        RefreshToken validToken = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(validToken));
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails)).thenReturn("new-fake-jwt-token");
        AuthResponse response = userService.refreshAccessToken(request);

        assertEquals("new-fake-jwt-token", response.getToken());
        assertEquals(rawRefreshToken, response.getRefreshToken(),
                "refreshAccessToken must echo back the same raw refresh token, not issue a new one");
        assertEquals("ROLE_USER", response.getRole());
        assertEquals("Token refreshed", response.getMessage());
    }

    @Test
    void logout_deletesRefreshTokenByItsHash() {
        RefreshTokenRequest request = new RefreshTokenRequest("raw-refresh-token");
        userService.logout(request);

        verify(refreshTokenRepository).deleteByTokenHash(anyString());
    }
}