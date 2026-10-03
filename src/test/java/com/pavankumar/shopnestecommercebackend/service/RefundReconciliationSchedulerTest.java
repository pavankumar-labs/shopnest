package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundReconciliationSchedulerTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private RefundReconciliationScheduler scheduler;

    @Test
    void reconcilePendingRefunds_shouldReconcileAllPendingPayments() {
        Payment paymentA = Payment.builder()
                .id(1L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();
        Payment paymentB = Payment.builder()
                .id(2L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();

        when(paymentRepository.findByStatus(PaymentStatus.REFUND_PENDING))
                .thenReturn(List.of(paymentA, paymentB));
        scheduler.reconcilePendingRefunds();

        verify(paymentRepository)
                .findByStatus(PaymentStatus.REFUND_PENDING);
        verify(paymentService).reconcileRefund(paymentA);
        verify(paymentService).reconcileRefund(paymentB);
    }

    @Test
    void reconcilePendingRefunds_shouldDoNothing_whenNoPendingRefundsExist() {
        when(paymentRepository.findByStatus(PaymentStatus.REFUND_PENDING))
                .thenReturn(List.of());
        scheduler.reconcilePendingRefunds();

        verify(paymentRepository)
                .findByStatus(PaymentStatus.REFUND_PENDING);
        verify(paymentService, never())
                .reconcileRefund(any(Payment.class));
    }

    @Test
    void reconcilePendingRefunds_shouldNotThrow_whenRefundReconciliationFails() {
        Payment payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();

        when(paymentRepository.findByStatus(PaymentStatus.REFUND_PENDING))
                .thenReturn(List.of(payment));
        doThrow(new RuntimeException("Refund reconciliation failed"))
                .when(paymentService)
                .reconcileRefund(payment);
        assertDoesNotThrow(
                () -> scheduler.reconcilePendingRefunds()
        );
        verify(paymentService).reconcileRefund(payment);
    }

    @Test
    void reconcilePendingRefunds_shouldContinueWithRemainingPayments_whenOneReconciliationFails() {
        Payment failedPayment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();
        Payment successfulPayment = Payment.builder()
                .id(2L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();
        Payment anotherPayment = Payment.builder()
                .id(3L)
                .status(PaymentStatus.REFUND_PENDING)
                .build();

        when(paymentRepository.findByStatus(PaymentStatus.REFUND_PENDING))
                .thenReturn(List.of(
                        failedPayment,
                        successfulPayment,
                        anotherPayment
                ));
        doThrow(new RuntimeException("Refund reconciliation failed"))
                .when(paymentService)
                .reconcileRefund(failedPayment);
        scheduler.reconcilePendingRefunds();

        verify(paymentService).reconcileRefund(failedPayment);
        verify(paymentService).reconcileRefund(successfulPayment);
        verify(paymentService).reconcileRefund(anotherPayment);
    }
}