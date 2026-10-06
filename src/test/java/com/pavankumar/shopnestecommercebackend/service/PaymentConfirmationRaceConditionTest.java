package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.dto.PaymentVerifyRequest;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.*;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PaymentConfirmationRaceConditionTest extends AbstractIntegrationTest {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WebhookService webhookService;

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
    private AddressRepository addressRepository;

    @MockitoBean
    private EmailService emailService;

    private static final String RAZORPAY_KEY_SECRET =
            "test-razorpay-key-secret";

    private static final String WEBHOOK_SECRET =
            "test-webhook-secret-12345";

    private String clientSignatureFor(
            String razorpayOrderId,
            String razorpayPaymentId) {

        return HmacUtils.hmacSha256Hex(
                RAZORPAY_KEY_SECRET,
                razorpayOrderId + "|" + razorpayPaymentId
        );
    }

    private String webhookSignatureFor(String payload) {
        return HmacUtils.hmacSha256Hex(
                WEBHOOK_SECRET,
                payload
        );
    }

    private String capturedWebhookPayload(
            String razorpayOrderId,
            String razorpayPaymentId) {
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
                """.formatted(
                razorpayOrderId,
                razorpayPaymentId
        );
    }

    private record Fixture(
            User user,
            Order order,
            Payment payment) {
    }

    private Fixture buildPendingOrderWithPayment(
            int sequence,
            String razorpayOrderId) {

        Category category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );
        Product product = productRepository.save(
                TestData.product(sequence, category).build()
        );
        User user = userRepository.save(
                TestData.uniqueUser().build()
        );
        UserAddress address = addressRepository.save(
                TestData.address(user).build()
        );
        Order order = TestData.order(
                        user,
                        address,
                        OrderStatus.PENDING
                )
                .build();

        order.getItems().add(
                TestData.orderItem(order, product).build()
        );

        order = orderRepository.save(order);
        Payment payment = paymentRepository.save(
                TestData.payment(
                        order,
                        razorpayOrderId
                ).build()
        );

        return new Fixture(
                user,
                order,
                payment
        );
    }

    @Test
    void verifyPaymentAndHandleWebhook_concurrentOnSamePayment_confirmExactlyOnce()
            throws Exception {
        String razorpayOrderId = "order_race_1";
        String razorpayPaymentId = "pay_race_1";

        Fixture fixture =
                buildPendingOrderWithPayment(
                        1,
                        razorpayOrderId
                );

        PaymentVerifyRequest clientRequest =
                new PaymentVerifyRequest(
                        razorpayOrderId,
                        razorpayPaymentId,
                        clientSignatureFor(
                                razorpayOrderId,
                                razorpayPaymentId
                        )
                );

        String webhookPayload =
                capturedWebhookPayload(
                        razorpayOrderId,
                        razorpayPaymentId
                );
        String webhookSignature =
                webhookSignatureFor(webhookPayload);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch =
                new CountDownLatch(2);
        CountDownLatch startLatch =
                new CountDownLatch(1);

        Callable<String> viaClient = () -> {
            readyLatch.countDown();
            startLatch.await();
            return paymentService.verifyPayment(
                    clientRequest
            );
        };

        Callable<String> viaWebhook = () -> {
            readyLatch.countDown();
            startLatch.await();
            return webhookService.handleWebhook(
                    webhookPayload,
                    webhookSignature,
                    "evt_race_1"
            );
        };

        Future<String> clientResult =
                executor.submit(viaClient);
        Future<String> webhookResult =
                executor.submit(viaWebhook);

        assertEquals(
                true,
                readyLatch.await(
                        5,
                        TimeUnit.SECONDS
                ),
                "both concurrent tasks should become ready"
        );

        startLatch.countDown();
        String clientOutcome =
                clientResult.get(
                        10,
                        TimeUnit.SECONDS
                );
        String webhookOutcome =
                webhookResult.get(
                        10,
                        TimeUnit.SECONDS
                );

        executor.shutdown();
        Payment reloadedPayment =
                paymentRepository
                        .findById(fixture.payment().getId())
                        .orElseThrow();

        Order reloadedOrder =
                orderRepository
                        .findById(fixture.order().getId())
                        .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                reloadedPayment.getStatus()
        );
        assertEquals(
                razorpayPaymentId,
                reloadedPayment.getRazorpayPaymentId()
        );
        assertEquals(
                OrderStatus.CONFIRMED,
                reloadedOrder.getStatus()
        );

        List<String> outcomes =
                List.of(
                        clientOutcome,
                        webhookOutcome
                );

        long confirmedCount =
                outcomes.stream()
                        .filter(o ->
                                o.equals(
                                        "Payment verified.  Order Confirmed."
                                )
                                        || o.equals(
                                        "Webhook processed successfully"
                                )
                        )
                        .count();

        assertEquals(
                1,
                confirmedCount,
                "exactly one path should have performed "
                        + "the confirmation; got: "
                        + outcomes
        );
        verify(
                emailService,
                times(1)
        ).sendOrderConfirmation(
                eq(fixture.user().getEmail()),
                eq(fixture.user().getName()),
                eq(fixture.order().getId()),
                argThat(amount ->
                        amount.compareTo(
                                fixture.order().getTotalAmount()
                        ) == 0
                )
        );
    }

    @Test
    void verifyPayment_calledTwiceSequentially_secondCallShortCircuits()
            throws Exception {

        String razorpayOrderId = "order_seq_1";
        String razorpayPaymentId = "pay_seq_1";

        Fixture fixture =
                buildPendingOrderWithPayment(
                        2,
                        razorpayOrderId
                );

        PaymentVerifyRequest request =
                new PaymentVerifyRequest(
                        razorpayOrderId,
                        razorpayPaymentId,
                        clientSignatureFor(
                                razorpayOrderId,
                                razorpayPaymentId
                        )
                );

        String firstResult =
                paymentService.verifyPayment(request);
        String secondResult =
                paymentService.verifyPayment(request);

        assertEquals(
                "Payment verified.  Order Confirmed.",
                firstResult
        );
        assertEquals(
                "Payment already verified",
                secondResult
        );
        verify(
                emailService,
                times(1)
        ).sendOrderConfirmation(
                eq(fixture.user().getEmail()),
                eq(fixture.user().getName()),
                eq(fixture.order().getId()),
                argThat(amount ->
                        amount.compareTo(
                                fixture.order().getTotalAmount()
                        ) == 0
                )
        );
    }
}