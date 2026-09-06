package com.pavankumar.shopnestecommercebackend.service;


import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReconciliationScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;


    @Scheduled(fixedDelayString = "${scheduler.refund-reconciliation-delay-ms}")
    public void reconcilePendingRefunds(){

        List<Payment> pendingPayments =
                paymentRepository.findByStatus(PaymentStatus.REFUND_PENDING);

        if (pendingPayments.isEmpty()) {
            return;
        }
        for (Payment payment : pendingPayments) {

            try {

                paymentService.reconcileRefund(payment);

            } catch (Exception e) {

                log.error(
                        "Failed to reconcile refund for payment ID: {}",
                        payment.getId(),
                        e
                );
            }
        }

    }



}
