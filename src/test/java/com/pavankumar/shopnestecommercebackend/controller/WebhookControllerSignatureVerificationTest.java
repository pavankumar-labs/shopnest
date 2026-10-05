package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.repository.WebhookEventRepository;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "razorpay.webhook.secret=test-webhook-secret-12345",
        "JWT_SECRET=test-jwt-secret-for-integration-tests-only",
        "JWT_EXPIRATION=3600000",
        "GOOGLE_CLIENT_ID=test-google-client-id",
        "GOOGLE_CLIENT_SECRET=test-google-client-secret",
        "BREVO_API_KEY=test-brevo-api-key",
        "BREVO_SENDER_EMAIL=test@shopnest.local",
        "BREVO_SENDER_NAME=ShopNest Test",
        "RAZORPAY_KEY_ID=test-razorpay-key-id",
        "RAZORPAY_KEY_SECRET=test-razorpay-key-secret",
        "APP_FRONTEND_URL=http://localhost:3000"
})
class WebhookControllerSignatureVerificationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WebhookEventRepository webhookEventRepository;

    private static final String WEBHOOK_SECRET = "test-webhook-secret-12345";
    private static final String WEBHOOK_URL = "/api/webhooks/razorpay";

    private String validSignatureFor(String payload) {
        return HmacUtils.hmacSha256Hex(WEBHOOK_SECRET, payload);
    }

    @Test
    void invalidSignature_rejectedWithBadRequest_andNoWebhookEventPersisted() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";
        long countBefore = webhookEventRepository.count();

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-razorpay-signature", "deliberately-wrong-signature")
                        .header("x-razorpay-event-id", "evt_invalid_sig_1")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"))
                .andExpect(jsonPath("$.path").value(WEBHOOK_URL));

        assertEquals(countBefore, webhookEventRepository.count(),
                "an invalid signature must be rejected before any webhook_events row is touched");
    }

    @Test
    void validSignature_unrecognizedEventType_acceptedAndRecorded() throws Exception {
        String payload = "{\"event\":\"some.unhandled.event.type\"}";
        String eventId = "evt_valid_sig_unhandled_1";
        String correctSignature = validSignatureFor(payload);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-razorpay-signature", correctSignature)
                        .header("x-razorpay-event-id", eventId)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Event received: some.unhandled.event.type"));
    }

    @Test
    void missingSignatureHeader_rejectedBySpringBeforeControllerLogicRuns() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-razorpay-event-id", "evt_missing_sig_1")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}