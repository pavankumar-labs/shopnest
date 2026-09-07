package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")

public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id",nullable = false,unique = true)
    private Order order;

    @Column(name = "razorpay_order_id",unique = true)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id",unique = true)
    private String razorpayPaymentId;

    @Column(name = "razorpay_refund_id", unique = true)
    private String razorpayRefundId;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false,precision = 10,scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt;
}
