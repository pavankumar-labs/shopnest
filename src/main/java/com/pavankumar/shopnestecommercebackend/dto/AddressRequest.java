package com.pavankumar.shopnestecommercebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
   private  String fullName;

    @NotBlank(message = "PhoneNumber is required")
    @Pattern(regexp = "^[0-9]{10}$",message = "PhoneNumber must be 10 digits")
   private String phoneNumber;

    @NotBlank(message = "Address is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
     private String city;

    @NotBlank(message = "State is  required")
   private String state;

    @NotBlank(message = "Country is required")
     private String country;


    @NotBlank(message = "Address is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be 6 digits")
     private String pincode;

}
