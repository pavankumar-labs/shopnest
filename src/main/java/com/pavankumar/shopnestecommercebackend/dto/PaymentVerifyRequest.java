package com.pavankumar.shopnestecommercebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class PaymentVerifyRequest {
    @NotBlank
    private  String razorpayOrderId;
    @NotBlank
    private String razorpayPaymentId;
    @NotBlank
    private String signature;

}
