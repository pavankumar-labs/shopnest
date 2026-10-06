package com.pavankumar.shopnestecommercebackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.dto.AddToCartRequest;
import com.pavankumar.shopnestecommercebackend.dto.RegisterRequest;
import com.pavankumar.shopnestecommercebackend.model.Cart;
import com.pavankumar.shopnestecommercebackend.model.CartItem;
import com.pavankumar.shopnestecommercebackend.model.Category;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.CartItemRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.CategoryRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class GlobalExceptionalHandlerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        org.springframework.security.core.userdetails.User
                                .withUsername(user.getEmail())
                                .password(user.getPassword())
                                .authorities(user.getRole().name())
                                .build(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        user.getRole().name()
                                )
                        )
                )
        );
    }

    @Test
    @Transactional
    void resourceNotFoundException_returns404() throws Exception {
        User user = userRepository.save(
                TestData.uniqueUser().build()
        );
        authenticateAs(user);

        mockMvc.perform(
                        get("/api/orders/99999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.message")
                                .value("Order Not Found: 99999")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/orders/99999")
                );
    }

    @Test
    @Transactional
    void unauthorisedException_crossUserCartItemRemoval_returns403()
            throws Exception {
        User owner = userRepository.save(
                TestData.uniqueUser().build()
        );
        User attacker = userRepository.save(
                TestData.uniqueUser().build()
        );

        Cart ownerCart = cartRepository.save(
                Cart.builder()
                        .user(owner)
                        .build()
        );
        Category category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );
        Product product = productRepository.save(
                TestData.product(1, category).build()
        );
        CartItem ownerItem = cartItemRepository.save(
                CartItem.builder()
                        .cart(ownerCart)
                        .product(product)
                        .quantity(1)
                        .build()
        );

        cartRepository.save(
                Cart.builder()
                        .user(attacker)
                        .build()
        );

        authenticateAs(attacker);
        mockMvc.perform(
                        delete("/api/cart/remove/" + ownerItem.getId())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "You cannot remove items from another user's cart"
                                )
                );
    }

    @Test
    @Transactional
    void accessDeniedException_nonAdminHittingAdminEndpoint_returns403()
            throws Exception {
        User regularUser = userRepository.save(
                TestData.uniqueUser().build()
        );
        authenticateAs(regularUser);

        mockMvc.perform(
                        put("/api/orders/1/status")
                                .param("newStatus", "CONFIRMED")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void resourceAlreadyExistsException_duplicateEmailRegistration_returns409()
            throws Exception {
        String duplicateEmail = "duplicate-" +
                java.util.UUID.randomUUID() +
                "@test.com";

        User existingUser = TestData.uniqueUser()
                .email(duplicateEmail)
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest();
        request.setName("Duplicate User");
        request.setEmail(duplicateEmail);
        request.setPassword("password123");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Email already registered: " +
                                                duplicateEmail
                                )
                );
    }

    @Test
    @Transactional
    void badRequestException_cartAddingMoreThanAvailable_returns400()
            throws Exception {

        User user = userRepository.save(
                TestData.uniqueUser().build()
        );

        authenticateAs(user);

        Category category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );
        Product product = productRepository.save(
                TestData.product(1, category)
                        .stock(2)
                        .build()
        );

        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(product.getId());
        request.setQuantity(5);
        mockMvc.perform(
                        post("/api/cart/add")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Insufficient stock. available: 2"
                                )
                );
    }

    @Test
    @Transactional
    void methodArgumentNotValidException_invalidQuantity_returns400WithFieldErrorMessage()
            throws Exception {
        User user = userRepository.save(
                TestData.uniqueUser().build()
        );

        authenticateAs(user);

        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);
        request.setQuantity(0);
        mockMvc.perform(
                        post("/api/cart/add")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "quantity:Quantity must at least 1"
                                )
                );
    }

    @Test
    @Transactional
    void unhandledException_returns500_withGenericSanitizedMessage()
            throws Exception {
        User user = userRepository.save(
                TestData.uniqueUser().build()
        );
        authenticateAs(user);

        mockMvc.perform(
                        get("/api/orders/not-a-valid-id")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.message")
                                .value("Internal server error")
                );
    }
}