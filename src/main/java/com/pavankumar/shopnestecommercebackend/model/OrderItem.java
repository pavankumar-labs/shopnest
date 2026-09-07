package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "order_items")
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch =FetchType.LAZY)
    @JoinColumn(name = "order_id",nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false,name = "price_at_purchase",precision = 10,scale = 2)
    private BigDecimal priceAtPurchase;
}
