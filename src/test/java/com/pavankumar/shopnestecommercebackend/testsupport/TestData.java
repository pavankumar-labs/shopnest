package com.pavankumar.shopnestecommercebackend.testsupport;

import com.pavankumar.shopnestecommercebackend.model.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class TestData {

    private TestData() {
    }

    public static Category.CategoryBuilder category(int sequence) {
        return Category.builder()
                .name("Test Category " + sequence);
    }

    public static Category.CategoryBuilder uniqueCategory() {
        return Category.builder()
                .name("Test Category " + UUID.randomUUID());
    }

    public static Product.ProductBuilder product(int sequence, Category category) {
        return Product.builder()
                .name("Test Product " + sequence)
                .price(BigDecimal.valueOf(999))
                .stock(10)
                .category(category);
    }

    public static User.UserBuilder user(int sequence) {
        return User.builder()
                .name("Test User " + sequence)
                .email("user" + sequence + "@test.com")
                .password("hashed-pw-" + sequence)
                .authType(AuthType.LOCAL)
                .role(Role.ROLE_USER);
    }

    public static User.UserBuilder uniqueUser() {
        String uniqueId = UUID.randomUUID().toString();

        return User.builder()
                .name("Test User " + uniqueId)
                .email("user-" + uniqueId + "@test.com")
                .password("hashed-pw-" + uniqueId)
                .authType(AuthType.LOCAL)
                .role(Role.ROLE_USER);
    }

    public static UserAddress.UserAddressBuilder address(User user) {
        return UserAddress.builder()
                .user(user)
                .fullName(user.getName())
                .phoneNumber("9999999999")
                .addressLine1("123 Test Street")
                .city("Hyderabad")
                .state("Telangana")
                .country("India")
                .pincode("500001");
    }

    public static Order.OrderBuilder order(
            User user,
            UserAddress address,
            OrderStatus status) {

        return Order.builder()
                .user(user)
                .userAddress(address)
                .status(status)
                .totalAmount(BigDecimal.valueOf(999));
    }

    public static OrderItem.OrderItemBuilder orderItem(
            Order order,
            Product product) {

        return OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(1)
                .priceAtPurchase(product.getPrice());
    }

    public static Payment.PaymentBuilder payment(
            Order order,
            String razorpayOrderId) {

        return Payment.builder()
                .order(order)
                .razorpayOrderId(razorpayOrderId)
                .status(PaymentStatus.CREATED)
                .amount(order.getTotalAmount());
    }
}