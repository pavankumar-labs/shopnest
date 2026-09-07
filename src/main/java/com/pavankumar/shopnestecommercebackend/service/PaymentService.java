package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import com.pavankumar.shopnestecommercebackend.dto.PaymentOrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PaymentVerifyRequest;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.exception.SignatureVerificationException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;
    private final AuthUtil util;
    private final RazorpayGateway razorpayGateway;
    private final InventoryService inventoryService;


    @Value("${razorpay.key.id}")
    private String key;

    @Value("${razorpay.key.secret}")
    private String secretKey;

    @Value("${razorpay.currency}")
    private String currency;
    
    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() throws RazorpayException {
        this.razorpayClient = new RazorpayClient(key, secretKey);
    }


    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long orderId ) throws RazorpayException {

        User user=util.getCurrentUser();
        Order order=orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(()->new ResourceNotFoundException("Order not found"));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Payment can only be created for pending orders");
        }

        Payment existingPayment = paymentRepository.findByOrderWithLock(order).orElse(null);

        if (existingPayment != null) {
            if (existingPayment.getStatus() == PaymentStatus.SUCCESS) {
                throw new BadRequestException("Payment already completed for this order");
            }

            return PaymentOrderResponse.builder()
                        .keyID(key)
                        .razorpayOrderId(existingPayment.getRazorpayOrderId())
                        .currency(currency)
                        .amount(existingPayment.getAmount())
                        .build();
        }

       long amountInPaise=order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100)).longValueExact();

        JSONObject orderRequest=new JSONObject();
        orderRequest.put("amount",amountInPaise);
        orderRequest.put("currency",currency);
        orderRequest.put("payment_capture", true);
        com.razorpay.Order razorpayOrder=razorpayClient.orders.create(orderRequest);
        Payment payment=Payment.builder()
                .razorpayOrderId(razorpayOrder.get("id"))
                .amount(order.getTotalAmount())
                .status(PaymentStatus.CREATED)
                .order(order)
                .build();

        paymentRepository.save(payment);

        return PaymentOrderResponse.builder()
                .keyID(key)
                .razorpayOrderId(razorpayOrder.get("id"))
                .currency(currency)
                .amount(order.getTotalAmount())
                .build();
    }

    @Transactional
    public  String verifyPayment(PaymentVerifyRequest paymentRequest) throws RazorpayException{

        Payment payment=paymentRepository
                .findByRazorpayOrderIdWithLock(paymentRequest.getRazorpayOrderId())
                .orElseThrow(()->new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return "Payment already verified";
        }

        JSONObject attributes=new JSONObject();
        attributes.put("razorpay_payment_id",paymentRequest.getRazorpayPaymentId());
        attributes.put("razorpay_order_id",payment.getRazorpayOrderId());
        attributes.put("razorpay_signature",paymentRequest.getSignature());

        boolean isValid= Utils.verifyPaymentSignature(attributes,secretKey);
        if(!(isValid)){

            throw new SignatureVerificationException("Payment Signature verification failed");
        }

        Order order=payment.getOrder();
        if(order.getStatus()!=OrderStatus.PENDING){
            return "Order already finalized. Refund required.";
        }

        payment.setRazorpayPaymentId(paymentRequest.getRazorpayPaymentId());
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

        return "Payment verified.  Order Confirmed.";
    }


    @Transactional
    public String initiateRefund(Payment payment) {

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException(
                    "Refund can only be initiated for a successful payment"
            );
        }


        String razorpayPaymentId =
                payment.getRazorpayPaymentId();

        if (razorpayPaymentId == null ||
                razorpayPaymentId.isBlank()) {

            throw new BadRequestException(
                    "Razorpay payment ID not found"
            );
        }

        String idempotencyKey =
                "shopnest-refund-" + payment.getId();

        RazorpayGateway.RefundResponse refundResponse =
                razorpayGateway.createRefund(
                        razorpayPaymentId,
                        payment.getAmount(),
                        idempotencyKey
                );

        payment.setRazorpayRefundId(
                refundResponse.refundId()
        );

        payment.setStatus(PaymentStatus.REFUND_PENDING);

        paymentRepository.save(payment);

        return "Refund initiated successfully";


    }

    @Transactional
    public void reconcileRefund(Payment payment) {

        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            return;
        }

        if (payment.getRazorpayRefundId() == null) {
            return;
        }

        RazorpayGateway.RefundResponse refundResponse =
                razorpayGateway.fetchRefund(
                        payment.getRazorpayRefundId()
                );

        String refundStatus =
                refundResponse.status();

        if ("pending".equalsIgnoreCase(refundStatus)) {

            return;
        }


        Order order = payment.getOrder();

        if ("processed".equalsIgnoreCase(refundStatus)) {

            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            inventoryService.restoreStock(order);

        }
        if ("failed".equalsIgnoreCase(refundStatus)) {

            payment.setStatus(PaymentStatus.REFUND_FAILED);
            paymentRepository.save(payment);

            order.setStatus(OrderStatus.CANCELLATION_REJECTED);
            orderRepository.save(order);

        }

    }}