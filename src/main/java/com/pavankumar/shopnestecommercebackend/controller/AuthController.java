package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.pavankumar.shopnestecommercebackend.dto.AuthResponse;
import com.pavankumar.shopnestecommercebackend.dto.LoginRequest;
import com.pavankumar.shopnestecommercebackend.dto.RegisterRequest;
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
}
