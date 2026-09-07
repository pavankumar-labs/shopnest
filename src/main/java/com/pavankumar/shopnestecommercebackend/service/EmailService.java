package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.service.Email.EmailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final BrevoEmailGateway brevoEmailGateway;

    @Async
    public void sendOrderConfirmation(String email, String userName, Long orderId, BigDecimal totalAmount) {
        String subject = "ShopNest — Order Confirmed #" + orderId;
        String html = EmailTemplates.orderConfirmation(userName, orderId, totalAmount);
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, userName, subject, html);
        if (!result.success()) {
            log.error("Order confirmation email failed for order {}: {}", orderId, result.errorMessage());
        } else {
            log.info("Order confirmation email sent for order {} (messageId={})", orderId, result.messageId());
        }
    }

    @Async
    public void sendPasswordReset(String email, String userName, String resetLink) {
        String subject = "ShopNest — Reset Your Password";
        String html = EmailTemplates.passwordReset(userName, resetLink);
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, userName, subject, html);
        if (!result.success()) {
            log.error("Password reset email failed for {}: {}", email, result.errorMessage());
        } else {
            log.info("Password reset email sent (messageId={})", result.messageId());
        }
    }

    @Async
    public void sendCancellationRequested(String email, String userName, Long orderId, BigDecimal amount) {
        String subject = "ShopNest — Cancellation Received #" + orderId;
        String html = EmailTemplates.cancellationRequested(userName, orderId, amount);
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, userName, subject, html);
        if (!result.success()) {
            log.error("Cancellation-requested email failed for order {}: {}", orderId, result.errorMessage());
        } else {
            log.info("Cancellation-requested email sent for order {} (messageId={})", orderId, result.messageId());
        }
    }

    @Async
    public void sendRefundProcessed(String email, String userName, Long orderId, BigDecimal amount) {
        String subject = "ShopNest — Refund Processed #" + orderId;
        String html = EmailTemplates.refundProcessed(userName, orderId, amount);
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, userName, subject, html);
        if (!result.success()) {
            log.error("Refund-processed email failed for order {}: {}", orderId, result.errorMessage());
        } else {
            log.info("Refund-processed email sent for order {} (messageId={})", orderId, result.messageId());
        }
    }

    @Async
    public void sendRefundFailed(String email, String userName, Long orderId, BigDecimal amount) {
        String subject = "ShopNest — Refund Issue #" + orderId;
        String html = EmailTemplates.refundFailed(userName, orderId, amount);
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, userName, subject, html);
        if (!result.success()) {
            log.error("Refund-failed email failed for order {}: {}", orderId, result.errorMessage());
        } else {
            log.info("Refund-failed email sent for order {} (messageId={})", orderId, result.messageId());
        }
    }
}
