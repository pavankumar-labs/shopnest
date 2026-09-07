package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Entity
@Data
@Table(name = "orders")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "order",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<OrderItem> items=new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name="total_amount",nullable = false,precision = 10,scale = 2)
    private BigDecimal totalAmount;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="shipping_address", nullable = false)
    private UserAddress userAddress;

    @Builder.Default
    @Column(name = "stock_restored", nullable = false)
    private boolean stockRestored = false;

    @CreationTimestamp
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt;

}
