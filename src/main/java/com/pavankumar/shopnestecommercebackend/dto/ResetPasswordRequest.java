package com.pavankumar.shopnestecommercebackend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @Size(min = 8, message = "Password must be 8+ characters")
    @NotBlank(message = "New password is required")
    private String newPassword;
}
