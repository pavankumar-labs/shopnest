package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.SignatureVerificationException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import com.pavankumar.shopnestecommercebackend.repository.WebhookEventRepository;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private WebhookService webhookService;

    private static final String WEBHOOK_SECRET = "test-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
    }

    private String sign(String payload) {
        return HmacUtils.hmacSha256Hex(WEBHOOK_SECRET, payload);
    }

    private String capturedPayload(String razorpayOrderId, String razorpayPaymentId) {
        return """
            {
              "event": "payment.captured",
              "payload": {
                "payment": {
                  "entity": {
                    "order_id": "%s",
                    "id": "%s"
                  }
                }
              }
            }
            """.formatted(razorpayOrderId, razorpayPaymentId);
    }

    private String failedPayload(String razorpayOrderId) {
        return """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "order_id": "%s"
                  }
                }
              }
            }
            """.formatted(razorpayOrderId);
    }

    private String eventOnlyPayload(String eventType) {
        return """
            {
              "event": "%s"
            }
            """.formatted(eventType);
    }

    @Test
    void handleWebhook_paymentCaptured_pendingOrder_confirmsOrderAndSendsEmail() {
        String payload = capturedPayload("order_rzp_1", "pay_rzp_1");
        String signature = sign(payload);

        User user = User.builder().id(1L).email("buyer@test.com").name("Buyer").build();
        Order order = Order.builder().id(10L).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(500)).user(user).build();
        Payment payment = Payment.builder().id(100L).order(order)
                .status(PaymentStatus.CREATED).razorpayOrderId("order_rzp_1")
                .amount(BigDecimal.valueOf(500)).build();

        when(paymentRepository.findByRazorpayOrderIdWithLock("order_rzp_1"))
                .thenReturn(Optional.of(payment));
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Webhook processed successfully", result);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.SUCCESS, paymentCaptor.getValue().getStatus());
        assertEquals("pay_rzp_1", paymentCaptor.getValue().getRazorpayPaymentId());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.CONFIRMED, orderCaptor.getValue().getStatus());

        verify(emailService).sendOrderConfirmation(
                "buyer@test.com", "Buyer", 10L, BigDecimal.valueOf(500));
        verify(webhookEventRepository).save(argThat(e -> e.getEventId().equals("evt-1")));
    }

    @Test
    void handleWebhook_invalidSignature_throwsAndTouchesNothing() {
        String realPayload = capturedPayload("order_rzp_1", "pay_rzp_1");
        String tamperedPayload = capturedPayload("order_rzp_999", "pay_rzp_1");
        String signatureForRealPayload = sign(realPayload);

        assertThrows(SignatureVerificationException.class, () ->
                webhookService.handleWebhook(tamperedPayload, signatureForRealPayload, "evt-1"));
        verifyNoInteractions(paymentRepository, orderRepository, emailService, webhookEventRepository);
    }

    @Test
    void handleWebhook_duplicateEventId_returnsAlreadyProcessedAndTouchesNothingElse() {
        String payload = capturedPayload("order_rzp_1", "pay_rzp_1");
        String signature = sign(payload);

        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(true);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Webhook event already processed", result);
        verifyNoInteractions(paymentRepository, orderRepository, emailService);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void handleWebhook_paymentCaptured_alreadySuccessful_returnsAlreadyProcessed() {
        String payload = capturedPayload("order_rzp_1", "pay_rzp_1");
        String signature = sign(payload);

        Payment payment = Payment.builder().id(100L).status(PaymentStatus.SUCCESS)
                .razorpayOrderId("order_rzp_1").build();

        when(paymentRepository.findByRazorpayOrderIdWithLock("order_rzp_1"))
                .thenReturn(Optional.of(payment));
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Already processed", result);
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(emailService);
        verify(webhookEventRepository).save(argThat(e -> e.getEventId().equals("evt-1")));
    }

    @Test
    void handleWebhook_paymentCaptured_orderNotPending_returnsRefundRequiredMessage() {
        String payload = capturedPayload("order_rzp_1", "pay_rzp_1");
        String signature = sign(payload);

        Order order = Order.builder().id(10L).status(OrderStatus.CANCELLED).build();
        Payment payment = Payment.builder().id(100L).status(PaymentStatus.CREATED)
                .razorpayOrderId("order_rzp_1").order(order).build();

        when(paymentRepository.findByRazorpayOrderIdWithLock("order_rzp_1"))
                .thenReturn(Optional.of(payment));
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Order already finalized. Refund required.", result);
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(emailService);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void handleWebhook_paymentFailed_notYetSuccessful_marksAttemptFailed() {
        String payload = failedPayload("order_rzp_1");
        String signature = sign(payload);
        Payment payment = Payment.builder().id(100L).status(PaymentStatus.CREATED)
                .razorpayOrderId("order_rzp_1").build();

        when(paymentRepository.findByRazorpayOrderIdWithLock("order_rzp_1"))
                .thenReturn(Optional.of(payment));
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Payment failure processed", result);
        verify(orderService).markPaymentAttemptFailed(payment);
        verify(webhookEventRepository).save(argThat(e -> e.getEventId().equals("evt-1")));
    }

    @Test
    void handleWebhook_paymentAuthorized_logsAndAwaitsCapture() {
        String payload = eventOnlyPayload("payment.authorized");
        String signature = sign(payload);

        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Payment authorized. Awaiting capture.", result);
        verifyNoInteractions(paymentRepository, orderRepository, emailService);
        verify(webhookEventRepository).save(argThat(e -> e.getEventId().equals("evt-1")));
    }

    @Test
    void handleWebhook_unrecognizedEventType_savesEventAndReturnsGenericMessage() {
        String payload = eventOnlyPayload("payment.dispute.created");
        String signature = sign(payload);

        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        String result = webhookService.handleWebhook(payload, signature, "evt-1");

        assertEquals("Event received: payment.dispute.created", result);
        verifyNoInteractions(paymentRepository, orderRepository, emailService);
        verify(webhookEventRepository).save(argThat(e -> e.getEventId().equals("evt-1")));
    }
}