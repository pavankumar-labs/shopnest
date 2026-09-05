package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.exception.SignatureVerificationException;
import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import com.pavankumar.shopnestecommercebackend.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavankumar.shopnestecommercebackend.model.*;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderService orderService;


    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Transactional
    public String handleWebhook(String payload,String signature, String eventId) {

        String extractSignature = HmacUtils.hmacSha256Hex(webhookSecret, payload);

        if (!(extractSignature.equals(signature))) {
            throw new SignatureVerificationException(
                    "Invalid webhook signature");
        }

        if (webhookEventRepository.existsByEventId(eventId)) {
            return "Webhook event already processed";
        }

        try {
            ObjectMapper objectMapper=new ObjectMapper();
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.get("event").asText();

            if ("payment.captured".equals(eventType)) {
                return handlePaymentCaptured(event, eventId);
            }

            if ("payment.failed".equals(eventType)) {
                return handlePaymentFailed(event, eventId);
            }

            if ("payment.authorized".equals(eventType)) {
                return handlePaymentAuthorized(event, eventId);
            }

            saveWebhookEvent(eventId);
            return "Event received: " + eventType;

        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Webhook processing failed", e);
        }
    }

        private String handlePaymentCaptured(JsonNode event, String eventId){

            String razorpayOrderId =
                    event
                            .get("payload")
                            .get("payment")
                            .get("entity")
                            .get("order_id")
                            .asText();
            String razorpayPaymentId =
                    event.
                            get("payload")
                            .get("payment")
                            .get("entity")
                            .get("id")
                            .asText();

            Payment payment = paymentRepository.findByRazorpayOrderIdWithLock(razorpayOrderId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Payment not found"));

            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                saveWebhookEvent(eventId);
                return "Already processed";
            }
            Order order = payment.getOrder();
            if(order.getStatus()!=OrderStatus.PENDING) {
                return "Order already finalized. Refund required.";
            }

            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);

            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            emailService.sendOrderConfirmation(
                    order.getUser().getEmail(),
                    order.getUser().getName(),
                    order.getId(),
                    order.getTotalAmount()
            );

            saveWebhookEvent(eventId);

            return "Webhook processed successfully";

        }

    private String handlePaymentAuthorized(
            JsonNode event,
            String eventId) {

        saveWebhookEvent(eventId);

        return "Payment authorized. Awaiting capture.";
    }

    private String handlePaymentFailed(
            JsonNode event,
            String eventId) {

        String razorpayOrderId = event
                .get("payload")
                .get("payment")
                .get("entity")
                .get("order_id")
                .asText();

        Payment payment = paymentRepository
                .findByRazorpayOrderIdWithLock(razorpayOrderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveWebhookEvent(eventId);
            return "Payment already successful";
        }

        orderService.markPaymentAttemptFailed(payment);
        saveWebhookEvent(eventId);

        return "Payment failure processed";
    }

    private void saveWebhookEvent(String eventId) {

        WebhookEvent webhookEvent = WebhookEvent.builder()
                .eventId(eventId)
                .build();

        webhookEventRepository.save(webhookEvent);
    }


}
