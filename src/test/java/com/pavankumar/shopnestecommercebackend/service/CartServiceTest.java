package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.AddToCartRequest;
import com.pavankumar.shopnestecommercebackend.dto.CartResponse;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.exception.UnauthorisedException;
import com.pavankumar.shopnestecommercebackend.model.Cart;
import com.pavankumar.shopnestecommercebackend.model.CartItem;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.CartItemRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private AuthUtil util;

    @InjectMocks
    private CartService cartService;

    private User user(Long id) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email("test@example.com")
                .build();
    }

    private Product product(
            Long id,
            String name,
            BigDecimal price,
            int stock
    ) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(price)
                .stock(stock)
                .build();
    }
    private Cart cart(
            Long id,
            User user,
            List<CartItem> items
    ) {
        return Cart.builder()
                .id(id)
                .user(user)
                .items(items)
                .build();
    }
    private CartItem cartItem(
            Long id,
            Cart cart,
            Product product,
            int quantity
    ) {
        return CartItem.builder()
                .id(id)
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .build();
    }

    @Test
    void getCart_shouldReturnExistingCartWithCalculatedTotals() {
        User user = user(1L);
        Product productA = product(
                101L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        Product productB = product(
                102L,
                "Mouse",
                BigDecimal.valueOf(50),
                20
        );
        Cart cart = cart(
                10L,
                user,
                new ArrayList<>()
        );
        CartItem itemA = cartItem(
                1001L,
                cart,
                productA,
                2
        );
        CartItem itemB = cartItem(
                1002L,
                cart,
                productB,
                3
        );
        cart.getItems().add(itemA);
        cart.getItems().add(itemB);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart();

        assertEquals(10L, response.getId());
        assertEquals(2, response.getCartItems().size());
        assertEquals(
                BigDecimal.valueOf(2150),
                response.getTotalAmount()
        );

        assertEquals(2, response.getTotalItems());
        assertEquals(
                BigDecimal.valueOf(2000),
                response.getCartItems()
                        .get(0)
                        .getSubTotal()
        );
        assertEquals(
                BigDecimal.valueOf(150),
                response.getCartItems()
                        .get(1)
                        .getSubTotal()
        );
        verify(util).getCurrentUser();
        verify(cartRepository)
                .findByUserIdWithItems(user.getId());
        verify(cartRepository, never())
                .save(any(Cart.class));
    }

    @Test
    void getCart_shouldCreateCart_whenCartDoesNotExist() {
        User user = user(1L);
        Cart createdCart = cart(
                20L,
                user,
                new ArrayList<>()
        );

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class)))
                .thenReturn(createdCart);

        CartResponse response = cartService.getCart();

        assertEquals(20L, response.getId());
        assertTrue(
                response.getCartItems().isEmpty()
        );
        assertEquals(
                BigDecimal.ZERO,
                response.getTotalAmount()
        );
        assertEquals(
                0,
                response.getTotalItems()
        );
        ArgumentCaptor<Cart> cartCaptor =
                ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository)
                .save(cartCaptor.capture());
        assertEquals(
                user,
                cartCaptor.getValue().getUser()
        );
        assertNotNull(
                cartCaptor.getValue().getItems()
        );
        assertTrue(
                cartCaptor.getValue().getItems().isEmpty()
        );
        verify(cartRepository)
                .findByUserIdWithItems(user.getId());
    }

    @Test
    void addToCart_shouldThrow_whenProductDoesNotExist() {
        User user = user(1L);

        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(100L);
        request.setQuantity(2);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(productRepository.findById(100L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.addToCart(request)
                );

        assertEquals(
                "Product not found: 100",
                exception.getMessage()
        );
        verify(productRepository)
                .findById(100L);
        verify(cartRepository, never())
                .findByUserId(any());
        verify(cartItemRepository, never())
                .save(any(CartItem.class));
    }

    @Test
    void addToCart_shouldThrow_whenRequestedQuantityExceedsStock() {
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                3
        );

        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(100L);
        request.setQuantity(5);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> cartService.addToCart(request)
                );

        assertTrue(
                exception.getMessage()
                        .contains("Insufficient stock")
        );
        verify(productRepository)
                .findById(100L);
        verify(cartRepository, never())
                .findByUserId(any());
        verify(cartItemRepository, never())
                .save(any(CartItem.class));
    }

    @Test
    void addToCart_shouldCreateCartItem_whenProductIsNotAlreadyInCart() {
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );

        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(100L);
        request.setQuantity(2);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(
                cart.getId(),
                product.getId()
        )).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.of(cart));

        CartResponse response =
                cartService.addToCart(request);

        assertEquals(
                cart.getId(),
                response.getId()
        );

        ArgumentCaptor<CartItem> itemCaptor =
                ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository)
                .save(itemCaptor.capture());
        CartItem savedItem =
                itemCaptor.getValue();

        assertEquals(
                cart,
                savedItem.getCart()
        );
        assertEquals(
                product,
                savedItem.getProduct()
        );
        assertEquals(
                2,
                savedItem.getQuantity()
        );
        verify(cartItemRepository)
                .findByCartIdAndProductId(
                        cart.getId(),
                        product.getId()
                );
    }

    @Test
    void addToCart_shouldIncreaseQuantity_whenItemAlreadyExistsAndStockIsSufficient() {
        // Arrange
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );
        CartItem existingItem = cartItem(
                200L,
                cart,
                product,
                3
        );
        cart.getItems().add(existingItem);
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(100L);
        request.setQuantity(2);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(
                cart.getId(),
                product.getId()
        )).thenReturn(Optional.of(existingItem));
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.of(cart));

        cartService.addToCart(request);

        assertEquals(
                5,
                existingItem.getQuantity()
        );
        verify(cartItemRepository)
                .save(existingItem);
    }

    @Test
    void addToCart_shouldThrow_whenCombinedQuantityExceedsStock() {
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                5
        );
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );
        CartItem existingItem = cartItem(
                200L,
                cart,
                product,
                4
        );
        cart.getItems().add(existingItem);
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(100L);
        request.setQuantity(2);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(
                cart.getId(),
                product.getId()
        )).thenReturn(Optional.of(existingItem));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> cartService.addToCart(request)
                );

        assertEquals(
                "Insufficient stock",
                exception.getMessage()
        );
        assertEquals(
                4,
                existingItem.getQuantity()
        );
        verify(cartItemRepository, never())
                .save(any(CartItem.class));
        verify(cartRepository, never())
                .findByUserIdWithItems(any());
    }

    @Test
    void removefromCart_shouldThrow_whenCartDoesNotExist() {
        User user = user(1L);
        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.removefromCart(100L)
                );

        assertEquals(
                "Cart is Not Found",
                exception.getMessage()
        );
        verify(cartItemRepository, never())
                .findById(any());
        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
    }

    @Test
    void removefromCart_shouldThrow_whenCartItemDoesNotExist() {
        User user = user(1L);
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(100L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.removefromCart(100L)
                );

        assertEquals(
                "CartItem Is not Found",
                exception.getMessage()
        );
        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
    }

    @Test
    void removefromCart_shouldThrow_whenCartItemBelongsToAnotherCart() {
        User user = user(1L);
        Cart userCart = cart(
                20L,
                user,
                new ArrayList<>()
        );
        Cart anotherCart = cart(
                30L,
                user(2L),
                new ArrayList<>()
        );
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        CartItem item = cartItem(
                200L,
                anotherCart,
                product,
                1
        );

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(userCart));
        when(cartItemRepository.findById(200L))
                .thenReturn(Optional.of(item));

        UnauthorisedException exception =
                assertThrows(
                        UnauthorisedException.class,
                        () -> cartService.removefromCart(200L)
                );

        assertEquals(
                "You cannot remove items from another user's cart",
                exception.getMessage()
        );
        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
    }

    @Test
    void removefromCart_shouldDeleteItem_whenItemBelongsToCurrentUsersCart() {
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );
        CartItem item = cartItem(
                200L,
                cart,
                product,
                1
        );
        cart.getItems().add(item);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(200L))
                .thenReturn(Optional.of(item));
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.of(cart));

        CartResponse response =
                cartService.removefromCart(200L);

        assertEquals(
                cart.getId(),
                response.getId()
        );
        verify(cartItemRepository)
                .delete(item);
        verify(cartRepository)
                .findByUserIdWithItems(user.getId());
    }

    @Test
    void clearCart_shouldThrow_whenCartDoesNotExist() {
        User user = user(1L);
        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.clearCart()
                );

        assertEquals(
                "Cart not found" + user.getId(),
                exception.getMessage()
        );
        verify(cartRepository, never())
                .save(any(Cart.class));
    }

    @Test
    void clearCart_shouldRemoveAllItemsAndSaveCart() {
        User user = user(1L);
        Product product = product(
                100L,
                "Laptop",
                BigDecimal.valueOf(1000),
                10
        );
        Cart cart = cart(
                20L,
                user,
                new ArrayList<>()
        );
        CartItem item = cartItem(
                200L,
                cart,
                product,
                2
        );
        cart.getItems().add(item);

        when(util.getCurrentUser())
                .thenReturn(user);
        when(cartRepository.findByUserIdWithItems(user.getId()))
                .thenReturn(Optional.of(cart));

        cartService.clearCart();

        assertTrue(
                cart.getItems().isEmpty()
        );
        verify(cartRepository)
                .save(cart);
    }
}