package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.model.Cart;
import com.pavankumar.shopnestecommercebackend.model.CartItem;
import com.pavankumar.shopnestecommercebackend.model.Category;
import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderItem;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.model.UserAddress;
import com.pavankumar.shopnestecommercebackend.repository.AddressRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartItemRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.CategoryRepository;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossUserDataIsolationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanAccessOwnOrder() throws Exception {
        User owner = createUser();
        Order order = createOrder(owner, OrderStatus.PENDING);
        authenticateAs(owner);

        mockMvc.perform(
                        get("/api/orders/{id}", order.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(order.getId()))
                .andExpect(jsonPath("$.data.status")
                        .value(OrderStatus.PENDING.name()));
    }

    @Test
    void userCannotAccessAnotherUsersOrder() throws Exception {
        User owner = createUser();
        User attacker = createUser();

        Order ownersOrder = createOrder(
                owner,
                OrderStatus.PENDING
        );
        authenticateAs(attacker);

        mockMvc.perform(
                        get("/api/orders/{id}", ownersOrder.getId())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(
                                "Order Not Found: "
                                        + ownersOrder.getId()
                        ));
    }

    @Test
    void userCannotCancelAnotherUsersOrder() throws Exception {
        User owner = createUser();
        User attacker = createUser();

        Order ownersOrder = createOrder(
                owner,
                OrderStatus.PENDING
        );
        authenticateAs(attacker);

        mockMvc.perform(
                        MockMvcRequestBuilders.put(
                                "/api/orders/{id}/cancel",
                                ownersOrder.getId()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(
                                "Order Not Found: "
                                        + ownersOrder.getId()
                        ));

        Order unchangedOrder = orderRepository
                .findById(ownersOrder.getId())
                .orElseThrow();
        assertEquals(
                OrderStatus.PENDING,
                unchangedOrder.getStatus(),
                "A user must not be able to change another user's order"
        );
    }

    @Test
    void rejectedCrossUserOrderOperationLeavesOwnersOrderUnchanged()
            throws Exception {
        User owner = createUser();
        User attacker = createUser();

        Order ownersOrder = createOrder(
                owner,
                OrderStatus.CONFIRMED
        );
        OrderStatus originalStatus = ownersOrder.getStatus();
        authenticateAs(attacker);

        mockMvc.perform(
                        MockMvcRequestBuilders.put(
                                "/api/orders/{id}/cancel",
                                ownersOrder.getId()
                        )
                )
                .andExpect(status().isNotFound());

        Order persistedOrder = orderRepository
                .findById(ownersOrder.getId())
                .orElseThrow();
        assertEquals(
                originalStatus,
                persistedOrder.getStatus(),
                "Rejected cross-user access must not modify "
                        + "the owner's order"
        );
    }

    @Test
    void userCannotRemoveAnotherUsersCartItem() throws Exception {
        User owner = createUser();
        User attacker = createUser();
        Cart ownersCart = createCart(owner);
        createCart(attacker);

        Product product = createProduct();
        CartItem ownersCartItem = CartItem.builder()
                .cart(ownersCart)
                .product(product)
                .quantity(1)
                .build();

        cartItemRepository.save(ownersCartItem);
        authenticateAs(attacker);

        mockMvc.perform(
                        delete(
                                "/api/cart/remove/{id}",
                                ownersCartItem.getId()
                        )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(
                                "You cannot remove items from "
                                        + "another user's cart"
                        ));

        assertTrue(
                cartItemRepository.existsById(
                        ownersCartItem.getId()
                ),
                "A rejected cross-user cart operation must not "
                        + "delete the item"
        );
    }

    @Test
    void ownerCanRemoveOwnCartItem() throws Exception {
        User owner = createUser();
        Cart cart = createCart(owner);
        Product product = createProduct();
        CartItem cartItem = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(1)
                .build();

        cartItemRepository.save(cartItem);
        authenticateAs(owner);

        mockMvc.perform(
                        delete(
                                "/api/cart/remove/{id}",
                                cartItem.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertFalse(
                cartItemRepository.existsById(cartItem.getId()),
                "The cart owner must be able to remove "
                        + "their own cart item"
        );
    }

    private User createUser() {
        return userRepository.save(
                TestData.uniqueUser().build()
        );
    }

    private UserAddress createAddress(User user) {
        return addressRepository.save(
                TestData.address(user).build()
        );
    }

    private Category createCategory() {
        return categoryRepository.save(
                TestData.uniqueCategory().build()
        );
    }

    private Product createProduct() {
        Category category = createCategory();

        return productRepository.save(
                TestData.product(1, category).build()
        );
    }

    private Cart createCart(User user) {
        return cartRepository.save(
                Cart.builder()
                        .user(user)
                        .items(new ArrayList<>())
                        .build()
        );
    }

    private Order createOrder(
            User user,
            OrderStatus status
    ) {
        UserAddress address = createAddress(user);
        Category category = createCategory();

        Product product = productRepository.save(
                TestData.product(1, category).build()
        );
        Order order = orderRepository.save(
                TestData.order(
                        user,
                        address,
                        status
                ).build()
        );

        OrderItem orderItem = TestData
                .orderItem(order, product)
                .build();
        order.getItems().add(orderItem);
        return orderRepository.save(order);
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        user.getRole().name()
                                )
                        )
                );
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}