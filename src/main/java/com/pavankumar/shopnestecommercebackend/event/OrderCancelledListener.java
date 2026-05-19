package com.pavankumar.shopnestecommercebackend.event;

import com.pavankumar.shopnestecommercebackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {

        try {
            emailService.sendOrderCancellation(
                    event.email(),
                    event.userName(),
                    event.orderId()
            );
        }
        catch (Exception e) {
            log.error(
                    "Failed to send cancellation email for order {}",
                    event.orderId(),
                    e
            );
        }
    }
}
