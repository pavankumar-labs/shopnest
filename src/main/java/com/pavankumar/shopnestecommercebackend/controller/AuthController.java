package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.*;
import com.pavankumar.shopnestecommercebackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Authentication management APIs")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements()
public class AuthController {
    private final UserService userService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register
            (@Valid @RequestBody RegisterRequest request){
        AuthResponse response=userService.register(request);
        return ResponseEntity.status(201).body(ApiResponse
                .success(response,"User successfully registered"));
    }
    @Operation(summary = "Login to existing User")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login
            (@Valid @RequestBody LoginRequest request){
        AuthResponse response=userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response,"Login successfully"));
    }

    @Operation(summary = "Change password (requires current password)")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request){

        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed", "Password updated successfully"));
    }

    @Operation(summary = "Request a password reset email")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ){
        userService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If that email exists, a reset link has been sent",
                "Request processed"));
    }

    @Operation(summary = "Reset password using a token from email")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset", "Password reset successfully"));
    }

    @Operation(summary = "Get a new access token using a refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = userService.refreshAccessToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @Operation(summary = "Logout — revokes the refresh token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        userService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out", "Logout successful"));
    }
}
