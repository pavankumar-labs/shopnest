package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.PaymentOrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PaymentVerifyRequest;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.exception.SignatureVerificationException;
import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AuthUtil util;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private RazorpayGateway razorpayGateway;

    @InjectMocks
    private PaymentService paymentService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(1L)
                .email("buyer@example.com")
                .name("Buyer")
                .build();

        ReflectionTestUtils.setField(paymentService, "key", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "currency", "INR");
        ReflectionTestUtils.setField(paymentService, "secretKey", "test_secret");
    }

    private Order pendingOrder(Long id, BigDecimal amount) {
        return Order.builder()
                .id(id)
                .status(OrderStatus.PENDING)
                .totalAmount(amount)
                .user(currentUser)
                .build();
    }

    private Order confirmedOrder(Long id, BigDecimal amount) {
        return Order.builder()
                .id(id)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(amount)
                .user(currentUser)
                .build();
    }

    @Nested
    class CreatePaymentOrder {
        @BeforeEach
        void stubCurrentUser() {
            when(util.getCurrentUser()).thenReturn(currentUser);
        }

        @Test
        void orderNotFoundForThisUser_throws() {
            when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

            ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.createPaymentOrder(5L));

            assertEquals("Order not found", thrown.getMessage());
            verifyNoInteractions(paymentRepository);
        }

        @Test
        void orderNotPending_throwsBadRequest() {
            Order order = confirmedOrder(5L, BigDecimal.valueOf(500));
            when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));

            BadRequestException thrown = assertThrows(BadRequestException.class,
                    () -> paymentService.createPaymentOrder(5L));

            assertEquals("Payment can only be created for pending orders", thrown.getMessage());
            verifyNoInteractions(paymentRepository);
        }

        @Test
        void existingPaymentAlreadySucceeded_throwsBadRequest() {
            Order order = pendingOrder(5L, BigDecimal.valueOf(500));
            Payment successfulPayment = Payment.builder()
                    .id(10L).order(order).status(PaymentStatus.SUCCESS).build();
            when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(successfulPayment));

            BadRequestException thrown = assertThrows(BadRequestException.class,
                    () -> paymentService.createPaymentOrder(5L));

            assertEquals("Payment already completed for this order", thrown.getMessage());
        }

        @Test
        void existingIncompletePayment_reusesItWithoutCallingRazorpay() throws Exception {
            Order order = pendingOrder(5L, BigDecimal.valueOf(500));
            Payment createdPayment = Payment.builder()
                    .id(10L).order(order).status(PaymentStatus.CREATED)
                    .razorpayOrderId("order_existing_123").amount(BigDecimal.valueOf(500)).build();

            when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(createdPayment));

            RazorpayClient mockClient = mock(RazorpayClient.class);
            ReflectionTestUtils.setField(paymentService, "razorpayClient", mockClient);

            PaymentOrderResponse response = paymentService.createPaymentOrder(5L);

            assertEquals("order_existing_123", response.getRazorpayOrderId());
            assertEquals(BigDecimal.valueOf(500), response.getAmount());
            assertEquals("rzp_test_key", response.getKeyID());
            assertEquals("INR", response.getCurrency());
            verifyNoInteractions(mockClient);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void noExistingPayment_createsNewRazorpayOrderAndSavesPayment() throws Exception {
            Order order = pendingOrder(5L, BigDecimal.valueOf(199.99));

            when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.empty());

            RazorpayClient mockClient = mock(RazorpayClient.class);
            OrderClient mockOrderClient = mock(OrderClient.class);
            com.razorpay.Order razorpayOrder = mock(com.razorpay.Order.class); // SDK's own Order — stays qualified, collides with our domain Order

            when(razorpayOrder.get("id")).thenReturn("order_new_456");
            when(mockOrderClient.create(any(JSONObject.class))).thenReturn(razorpayOrder);

            ReflectionTestUtils.setField(paymentService, "razorpayClient", mockClient);
            ReflectionTestUtils.setField(mockClient, "orders", mockOrderClient);
            PaymentOrderResponse response = paymentService.createPaymentOrder(5L);

            assertEquals("order_new_456", response.getRazorpayOrderId());
            assertEquals(BigDecimal.valueOf(199.99), response.getAmount());
            assertEquals("rzp_test_key", response.getKeyID());
            assertEquals("INR", response.getCurrency());

            ArgumentCaptor<JSONObject> requestCaptor = ArgumentCaptor.forClass(JSONObject.class);
            verify(mockOrderClient).create(requestCaptor.capture());
            JSONObject request = requestCaptor.getValue();
            assertEquals(19999L, request.getLong("amount"));
            assertEquals("INR", request.getString("currency"));
            assertTrue(request.getBoolean("payment_capture"));

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertEquals("order_new_456", savedPayment.getRazorpayOrderId());
            assertEquals(PaymentStatus.CREATED, savedPayment.getStatus());
            assertEquals(BigDecimal.valueOf(199.99), savedPayment.getAmount());
            assertEquals(order, savedPayment.getOrder());
        }
    }

    @Nested
    class VerifyPayment {

        private PaymentVerifyRequest validRequest() {
            PaymentVerifyRequest request = new PaymentVerifyRequest();
            request.setRazorpayOrderId("order_abc");
            request.setRazorpayPaymentId("pay_xyz");
            request.setSignature("sig_123");
            return request;
        }

        @Test
        void paymentNotFound_throws() throws Exception {
            when(paymentRepository.findByRazorpayOrderIdWithLock("order_abc")).thenReturn(Optional.empty());

            ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.verifyPayment(validRequest()));

            assertEquals("Payment not found", thrown.getMessage());
        }

        @Test
        void alreadySuccess_returnsImmediately_neverChecksSignatureOrSendsEmail() throws Exception {
            Order order = pendingOrder(5L, BigDecimal.valueOf(500));
            Payment payment = Payment.builder().id(10L).order(order).status(PaymentStatus.SUCCESS).build();

            when(paymentRepository.findByRazorpayOrderIdWithLock("order_abc")).thenReturn(Optional.of(payment));

            try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
                String result = paymentService.verifyPayment(validRequest());

                assertEquals("Payment already verified", result);
                utils.verifyNoInteractions();
            }
            verifyNoInteractions(emailService);
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void invalidSignature_throwsAndChangesNothing() throws Exception {
            Order order = pendingOrder(5L, BigDecimal.valueOf(500));
            Payment payment = Payment.builder()
                    .id(10L).order(order).status(PaymentStatus.CREATED).razorpayOrderId("order_abc").build();

            when(paymentRepository.findByRazorpayOrderIdWithLock("order_abc")).thenReturn(Optional.of(payment));

            try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
                utils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                        .thenReturn(false);
                SignatureVerificationException thrown = assertThrows(SignatureVerificationException.class,
                        () -> paymentService.verifyPayment(validRequest()));

                assertEquals("Payment Signature verification failed", thrown.getMessage());
            }
            assertEquals(PaymentStatus.CREATED, payment.getStatus());
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(emailService);
        }

        @Test
        void validSignature_orderAlreadyFinalized_returnsMessageWithoutConfirming() throws Exception {
            Order cancelledOrder = Order.builder()
                    .id(5L).status(OrderStatus.CANCELLED)
                    .totalAmount(BigDecimal.valueOf(500)).user(currentUser).build();

            Payment payment = Payment.builder()
                    .id(10L).order(cancelledOrder).status(PaymentStatus.CREATED).razorpayOrderId("order_abc").build();

            when(paymentRepository.findByRazorpayOrderIdWithLock("order_abc")).thenReturn(Optional.of(payment));
            try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
                utils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                        .thenReturn(true);

                String result = paymentService.verifyPayment(validRequest());

                assertEquals("Order already finalized. Refund required.", result);
            }
            assertEquals(PaymentStatus.CREATED, payment.getStatus());
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(emailService);
        }

        @Test
        void validSignature_orderPending_confirmsOrderAndSendsEmail() throws Exception {
            Order order = pendingOrder(5L, BigDecimal.valueOf(500));
            Payment payment = Payment.builder()
                    .id(10L).order(order).status(PaymentStatus.CREATED).razorpayOrderId("order_abc").build();

            when(paymentRepository.findByRazorpayOrderIdWithLock("order_abc")).thenReturn(Optional.of(payment));

            try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
                utils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                        .thenReturn(true);
                String result = paymentService.verifyPayment(validRequest());
                assertEquals("Payment verified.  Order Confirmed.", result);
            }

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertEquals(PaymentStatus.SUCCESS, paymentCaptor.getValue().getStatus());
            assertEquals("pay_xyz", paymentCaptor.getValue().getRazorpayPaymentId());

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertEquals(OrderStatus.CONFIRMED, orderCaptor.getValue().getStatus());
            verify(emailService).sendOrderConfirmation("buyer@example.com", "Buyer", 5L, BigDecimal.valueOf(500));
        }
    }

    @Nested
    class InitiateRefund {

        @Test
        void nonSuccessfulPayment_throwsBadRequest() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.CREATED).amount(BigDecimal.valueOf(500)).build();

            BadRequestException thrown = assertThrows(BadRequestException.class,
                    () -> paymentService.initiateRefund(payment));

            assertEquals("Refund can only be initiated for a successful payment", thrown.getMessage());
            verifyNoInteractions(razorpayGateway);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void successfulPaymentWithoutRazorpayPaymentId_throwsBadRequest() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.SUCCESS).amount(BigDecimal.valueOf(500))
                    .razorpayPaymentId(null).build();

            BadRequestException thrown = assertThrows(BadRequestException.class,
                    () -> paymentService.initiateRefund(payment));

            assertEquals("Razorpay payment ID not found", thrown.getMessage());
            verifyNoInteractions(razorpayGateway);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void successfulPaymentWithBlankRazorpayPaymentId_throwsBadRequest() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.SUCCESS).amount(BigDecimal.valueOf(500))
                    .razorpayPaymentId("   ").build();

            BadRequestException thrown = assertThrows(BadRequestException.class,
                    () -> paymentService.initiateRefund(payment));

            assertEquals("Razorpay payment ID not found", thrown.getMessage());
            verifyNoInteractions(razorpayGateway);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void successfulPayment_initiatesRefundAndMarksPaymentPending() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.SUCCESS).amount(BigDecimal.valueOf(500))
                    .razorpayPaymentId("pay_123").build();

            RazorpayGateway.RefundResponse refundResponse =
                    new RazorpayGateway.RefundResponse("rfnd_123", "created");

            when(razorpayGateway.createRefund(
                    eq("pay_123"), eq(BigDecimal.valueOf(500)), eq("shopnest-refund-10")))
                    .thenReturn(refundResponse);
            String result = paymentService.initiateRefund(payment);

            assertEquals("Refund initiated successfully", result);
            assertEquals("rfnd_123", payment.getRazorpayRefundId());
            assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());
            verify(razorpayGateway).createRefund("pay_123", BigDecimal.valueOf(500), "shopnest-refund-10");
            verify(paymentRepository).save(payment);
        }
    }

    @Nested
    class ReconcileRefund {

        @Test
        void paymentNotRefundPending_returnsWithoutCallingGateway() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.SUCCESS).razorpayRefundId("rfnd_123").build();
            paymentService.reconcileRefund(payment);

            verifyNoInteractions(razorpayGateway);
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void refundPendingWithoutRefundId_returnsWithoutCallingGateway() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.REFUND_PENDING).razorpayRefundId(null).build();
            paymentService.reconcileRefund(payment);

            verifyNoInteractions(razorpayGateway);
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void refundStillPending_doesNothing() {
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.REFUND_PENDING).razorpayRefundId("rfnd_123").build();
            RazorpayGateway.RefundResponse refundResponse =
                    new RazorpayGateway.RefundResponse("rfnd_123", "pending");

            when(razorpayGateway.fetchRefund("rfnd_123")).thenReturn(refundResponse);
            paymentService.reconcileRefund(payment);

            verify(razorpayGateway).fetchRefund("rfnd_123");
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void processedRefund_marksPaymentRefunded_cancelsOrderAndRestoresStock() {
            Order order = confirmedOrder(5L, BigDecimal.valueOf(500));

            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.REFUND_PENDING)
                    .razorpayRefundId("rfnd_123").razorpayPaymentId("pay_123")
                    .amount(BigDecimal.valueOf(500)).order(order).build();
            RazorpayGateway.RefundResponse refundResponse =
                    new RazorpayGateway.RefundResponse("rfnd_123", "processed");

            when(razorpayGateway.fetchRefund("rfnd_123")).thenReturn(refundResponse);
            paymentService.reconcileRefund(payment);

            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            verify(paymentRepository).save(payment);
            verify(orderRepository).save(order);
            verify(inventoryService).restoreStock(order);
            verify(razorpayGateway).fetchRefund("rfnd_123");
        }

        @Test
        void failedRefund_marksPaymentRefundFailed_andRejectsCancellation() {
            Order order = confirmedOrder(5L, BigDecimal.valueOf(500));
            Payment payment = Payment.builder()
                    .id(10L).status(PaymentStatus.REFUND_PENDING)
                    .razorpayRefundId("rfnd_123").razorpayPaymentId("pay_123")
                    .amount(BigDecimal.valueOf(500)).order(order).build();
            RazorpayGateway.RefundResponse refundResponse =
                    new RazorpayGateway.RefundResponse("rfnd_123", "failed");

            when(razorpayGateway.fetchRefund("rfnd_123")).thenReturn(refundResponse);
            paymentService.reconcileRefund(payment);

            assertEquals(PaymentStatus.REFUND_FAILED, payment.getStatus());
            assertEquals(OrderStatus.CANCELLATION_REJECTED, order.getStatus());
            verify(paymentRepository).save(payment);
            verify(orderRepository).save(order);
            verifyNoInteractions(inventoryService);
            verify(razorpayGateway).fetchRefund("rfnd_123");
        }
    }
}