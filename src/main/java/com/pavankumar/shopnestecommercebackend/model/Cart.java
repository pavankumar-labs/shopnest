package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.CascadeType;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "carts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",unique = true,nullable = false)
    private User user;

    @ToString.Exclude
    @OneToMany(mappedBy = "cart", fetch = FetchType.LAZY,cascade = CascadeType.ALL,orphanRemoval=true)
    private List<CartItem> items=new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt;
}
