package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.*;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceAlreadyExistsException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.PasswordResetTokenRepository;
import com.pavankumar.shopnestecommercebackend.repository.RefreshTokenRepository;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.security.JwtUtil;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class UserService  {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private  Counter successCounter;
    private   Counter failCounter;
    private final MeterRegistry registry;
    private final AuthUtil util;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;

    @PostConstruct
    public void initMetrics(){
        this.successCounter= Counter.builder("shopnest.login.attempts")
                .tag("result", "success")
                .description("Total successful login attempts")
                .register(registry);
        this.failCounter= Counter.builder("shopnest.login.attempts")
                .tag("result", "failure")
                .description("Total failed login attempts")
                .register(registry);
    }


    public AuthResponse register(RegisterRequest request){
        if(userRepository.existsByEmail(request.getEmail())){
            throw new ResourceAlreadyExistsException
                    ("Email already registered: "+request.getEmail());
        }
        User user=User.builder()
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ROLE_USER)
                .authType(AuthType.LOCAL)
                .build();
        userRepository.save(user);
        return AuthResponse.builder()
                .role(user.getRole().name())
                .message("registration successfully")
                .build();
    }
    public AuthResponse login(LoginRequest request){

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        if (user.getAuthType() == AuthType.GOOGLE) {

            throw new BadCredentialsException(
                    "This account can only be accessed using Google login"
            );
        }

        try{
            Authentication authentication=authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(),request.getPassword()));
            UserDetails userDetails=(UserDetails) authentication.getPrincipal();
            String token= jwtUtil.generateToken(userDetails);
            String refreshToken = issueRefreshToken(user);
            successCounter.increment();
            return AuthResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .role(jwtUtil.extractRoleFromUserDetails(userDetails))
                    .message("login successful")
                    .build();
        } catch (BadCredentialsException e) {
            failCounter.increment();
             throw e;
        }
    }


    @Transactional
    public void changePassword(ChangePasswordRequest request){

        User user = util.getCurrentUser();
        if (user.getAuthType() != AuthType.LOCAL) {
            throw new BadRequestException(
                    "This account uses Google Sign-In — there's no password to change");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteAllByUser(user);
    }

    public AuthResponse loginWithGoogle(
            String googleId,
            String email,
            String name
    ){
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() ->
                        userRepository.findByEmail(email)
                                .map(existingUser -> linkGoogleToExistingUser(
                                        existingUser
                                ))
                                .orElseGet(() ->
                                        createGoogleUser(
                                                googleId,
                                                email,
                                                name
                                        )
                                )
                );

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .authorities(user.getRole().name())
                        .build();

        String token = jwtUtil.generateToken(userDetails);
        String refreshToken = issueRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .message("Google login successful")
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request){
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user != null && user.getAuthType() == AuthType.LOCAL){

            passwordResetTokenRepository.deleteAllUnusedByUser(user);

            String rawToken = generateSecureToken();
            String tokenHash = sha256Hex(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            emailService.sendPasswordReset(user.getEmail(), user.getName(), resetLink);

        }

    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request){

        String tokenHash = sha256Hex(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired reset link");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteAllByUser(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    private User linkGoogleToExistingUser(
            User user
    ){
        if (user.getAuthType() == AuthType.LOCAL) {
            user.setAuthType(AuthType.BOTH);
        }

        return userRepository.save(user);
    }

    private User createGoogleUser(
            String googleId,
            String email,
            String name
    ){
        String randomPassword = generateRandomPassword();

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(randomPassword))
                .role(Role.ROLE_USER)
                .authType(AuthType.GOOGLE)
                .googleId(googleId)
                .build();

        return userRepository.save(user);
    }


    private String generateRandomPassword() {

        SecureRandom secureRandom = new SecureRandom();

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String issueRefreshToken(User user){

        String rawToken = generateSecureToken();
        String tokenHash= sha256Hex(rawToken);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public AuthResponse refreshAccessToken(RefreshTokenRequest request){
        String tokenHash = sha256Hex(request.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token expired, please login again");
        }

        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .role(user.getRole().name())
                .message("Token refreshed")
                .build();
    }

    public void logout(RefreshTokenRequest request) {
        String tokenHash = sha256Hex(request.getRefreshToken());
        refreshTokenRepository.deleteByTokenHash(tokenHash);
    }

}
