package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.*;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import jakarta.persistence.EntityManager;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
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
class WebhookServiceIdempotencyTest extends AbstractIntegrationTest {
    private static final String WEBHOOK_SECRET = "test-webhook-secret-12345";

    @Autowired
    private WebhookService webhookService;
    @Autowired
    private WebhookEventRepository webhookEventRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmailService emailService;

    private String sign(String payload) {
        return HmacUtils.hmacSha256Hex(WEBHOOK_SECRET, payload);
    }

    private String paymentCapturedPayload(String razorpayOrderId, String razorpayPaymentId) {
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

    private UserAddress persistAddress(User user) {
        UserAddress address = TestData.address(user).build();
        entityManager.persist(address);
        entityManager.flush();
        return address;
    }

    @Test
    @Transactional
    void duplicateWebhookDelivery_secondCallIsCompleteNoOp() {
        Category category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );
        Product product = productRepository.save(TestData.product(1, category).build());
        User user = userRepository.save(
                TestData.uniqueUser().build()
        );
        UserAddress address = persistAddress(user);

        Order order = orderRepository.save(TestData.order(user, address, OrderStatus.PENDING).build());
        Payment payment = paymentRepository.save(TestData.payment(order, "order_abc_123").build());

        String payload = paymentCapturedPayload("order_abc_123", "pay_xyz_456");
        String signature = sign(payload);
        String eventId = "evt_duplicate_test_1";

        String firstResult = webhookService.handleWebhook(payload, signature, eventId);
        assertEquals("Webhook processed successfully", firstResult);

        Order confirmedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());

        String secondResult = webhookService.handleWebhook(payload, signature, eventId);
        assertEquals("Webhook event already processed", secondResult);

        Order orderAfterReplay = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, orderAfterReplay.getStatus());

        Payment paymentAfterReplay = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, paymentAfterReplay.getStatus());
        verify(emailService, times(1))
                .sendOrderConfirmation(anyString(), anyString(), anyLong(), any(BigDecimal.class));

        List<WebhookEvent> allEvents = webhookEventRepository.findAll();
        long matchingEventCount = allEvents.stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();
        assertEquals(1, matchingEventCount,
                "a replayed webhook with the same eventId must not create a second webhook_events row");
    }

    @Test
    void concurrentDuplicateDelivery_uniqueConstraintPreventsDoubleProcessing() throws Exception {
        String payload = """
                {"event":"some.unhandled.race.event"}
                """;
        String signature = sign(payload);
        String eventId = "evt_concurrent_race_1";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable deliverWebhook = () -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                webhookService.handleWebhook(payload, signature, eventId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        executor.submit(deliverWebhook);
        executor.submit(deliverWebhook);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<WebhookEvent> allEvents = webhookEventRepository.findAll();
        long matchingRows = allEvents.stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();

        assertEquals(1, matchingRows,
                "even under a real race, the unique constraint on event_id must " +
                        "prevent more than one webhook_events row for the same eventId");
        assertEquals(2, successCount.get() + failureCount.get(),
                "both threads must have completed (successfully or not) — none left hanging");
    }
}