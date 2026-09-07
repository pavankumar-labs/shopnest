package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "cart_items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id",nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;
}
