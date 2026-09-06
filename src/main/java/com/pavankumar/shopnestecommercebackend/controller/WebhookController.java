package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.pavankumar.shopnestecommercebackend.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<ApiResponse<String>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("x-razorpay-signature") String signature,
            @RequestHeader("x-razorpay-event-id") String eventId) {
        String response = webhookService.handleWebhook(payload, signature,eventId);
        return ResponseEntity.status(200)
                .body(ApiResponse.success(response, "Webhook payment verified"));
    }
}