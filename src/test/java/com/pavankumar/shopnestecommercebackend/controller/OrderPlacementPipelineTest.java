package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.model.Cart;
import com.pavankumar.shopnestecommercebackend.model.CartItem;
import com.pavankumar.shopnestecommercebackend.model.Category;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.model.UserAddress;
import com.pavankumar.shopnestecommercebackend.repository.AddressRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartItemRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.CategoryRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
class OrderPlacementPipelineTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AddressRepository addressRepository;

    private Category category;
    private User user;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );

        user = userRepository.save(
                TestData.uniqueUser().build()
        );
        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .authorities(user.getRole().name())
                        .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        user.getRole().name()
                                )
                        )
                )
        );
    }

    @AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Product persistProduct(
            int sequence,
            int stock,
            BigDecimal price
    ) {
        return productRepository.save(
                TestData.product(sequence, category)
                        .stock(stock)
                        .price(price)
                        .build()
        );
    }

    private UserAddress persistAddress(User addressOwner) {
        UserAddress address =
                TestData.address(addressOwner).build();
        return addressRepository.save(address);
    }

    private Cart persistCartWithItems(Product... products) {
        Cart cart = Cart.builder()
                .user(user)
                .build();
        cart.setItems(new ArrayList<>());
        cart = cartRepository.save(cart);

        for (Product product : products) {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(2)
                    .build();

            cartItemRepository.save(cartItem);
            cart.getItems().add(cartItem);
        }
        return cart;
    }

    @Test
    void placeOrder_happyPath_createsOrder_deductsStock_andClearsCart()
            throws Exception {
        Product productA = persistProduct(
                1,
                10,
                BigDecimal.valueOf(100)
        );
        Product productB = persistProduct(
                2,
                5,
                BigDecimal.valueOf(200)
        );
        persistCartWithItems(productA, productB);

        UserAddress address = persistAddress(user);
        String request = """
                {
                  "addressId": %d
                }
                """.formatted(address.getId());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status")
                        .value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.totalAmount")
                        .value(600));

        Product productAAfter =
                productRepository.findById(productA.getId())
                        .orElseThrow();
        Product productBAfter =
                productRepository.findById(productB.getId())
                        .orElseThrow();

        assertEquals(
                8,
                productAAfter.getStock(),
                "product A stock should be reduced by 2"
        );
        assertEquals(
                3,
                productBAfter.getStock(),
                "product B stock should be reduced by 2"
        );

        Cart cartAfter =
                cartRepository.findByUserIdWithItems(user.getId())
                        .orElseThrow();
        assertTrue(
                cartAfter.getItems().isEmpty(),
                "cart should be empty after successful order placement"
        );
    }

    @Test
    void placeOrder_emptyCart_returnsBadRequest_andDoesNotChangeStock()
            throws Exception {
        Product product = persistProduct(
                3,
                10,
                BigDecimal.valueOf(100)
        );

        Cart cart = Cart.builder()
                .user(user)
                .build();
        cart.setItems(new ArrayList<>());
        cartRepository.save(cart);

        UserAddress address = persistAddress(user);
        String request = """
                {
                  "addressId": %d
                }
                """.formatted(address.getId());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Cannot place order with empty cart"));

        Product productAfter =
                productRepository.findById(product.getId())
                        .orElseThrow();

        assertEquals(
                10,
                productAfter.getStock(),
                "stock must remain unchanged when cart is empty"
        );
    }

    @Test
    void placeOrder_withoutCart_returnsNotFound()
            throws Exception {
        UserAddress address = persistAddress(user);
        String request = """
                {
                  "addressId": %d
                }
                """.formatted(address.getId());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void placeOrder_insufficientStock_rollsBackAllStockDeductions()
            throws Exception {
        Product productA = persistProduct(
                4,
                10,
                BigDecimal.valueOf(100)
        );
        Product productB = persistProduct(
                5,
                1,
                BigDecimal.valueOf(200)
        );

        persistCartWithItems(productA, productB);

        UserAddress address = persistAddress(user);
        String request = """
                {
                  "addressId": %d
                }
                """.formatted(address.getId());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnprocessableEntity());

        Product productAAfter =
                productRepository.findById(productA.getId())
                        .orElseThrow();
        Product productBAfter =
                productRepository.findById(productB.getId())
                        .orElseThrow();

        assertEquals(
                10,
                productAAfter.getStock(),
                "stock must roll back when the second product fails"
        );
        assertEquals(
                1,
                productBAfter.getStock(),
                "insufficient-stock product must remain unchanged"
        );
    }

    @Test
    void placeOrder_addressNotOwnedByUser_rollsBackAllStockDeductions()
            throws Exception {
        Product productA = persistProduct(
                6,
                10,
                BigDecimal.valueOf(100)
        );
        Product productB = persistProduct(
                7,
                10,
                BigDecimal.valueOf(200)
        );

        persistCartWithItems(productA, productB);

        User otherUser = userRepository.save(
                TestData.uniqueUser().build()
        );
        UserAddress otherUsersAddress =
                persistAddress(otherUser);

        String request = """
                {
                  "addressId": %d
                }
                """.formatted(otherUsersAddress.getId());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());

        Product productAAfter =
                productRepository.findById(productA.getId())
                        .orElseThrow();
        Product productBAfter =
                productRepository.findById(productB.getId())
                        .orElseThrow();

        assertEquals(
                10,
                productAAfter.getStock(),
                "stock must roll back when address ownership check fails"
        );
        assertEquals(
                10,
                productBAfter.getStock(),
                "stock must roll back when address ownership check fails"
        );
    }
}