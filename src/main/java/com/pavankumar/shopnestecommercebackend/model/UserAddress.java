package com.pavankumar.shopnestecommercebackend.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "user_addresses")

public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(nullable = false) private  String fullName;

    @Column(nullable = false) private String phoneNumber;

    @Column(nullable = false)  private String addressLine1;

    private String addressLine2;

    @Column(nullable = false) private String city;

    @Column(nullable = false) private String state;

    @Column(nullable = false) private String country;

    @Column(nullable = false)  private String pincode;

    @Builder.Default
    @Column(name = "is_default",nullable = false)
    private Boolean isDefault=false;


}
