package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.OrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PlaceOrderRequest;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private CartRepository cartRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AuthUtil util;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private StockService stockService;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrder_shouldCreateOrderAndClearCart_whenRequestIsValid() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);
        Product product = createProduct(
                101L,
                "Laptop",
                "50000.00"
        );

        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getProduct()).thenReturn(product);
        when(cartItem.getQuantity()).thenReturn(2);
        List<CartItem> cartItems = new ArrayList<>();
        cartItems.add(cartItem);
        Cart cart = mock(Cart.class);
        when(cart.getItems()).thenReturn(cartItems);

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));
        UserAddress address = createAddress(
                10L,
                "123 Main Street",
                "517501"
        );
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAddressId(10L);

        when(addressRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(request);
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals(user, savedOrder.getUser());
        assertEquals(address, savedOrder.getUserAddress());
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertEquals(
                new BigDecimal("100000.00"),
                savedOrder.getTotalAmount()
        );
        assertEquals(1, savedOrder.getItems().size());

        OrderItem orderItem = savedOrder.getItems().get(0);
        assertEquals(product, orderItem.getProduct());
        assertEquals(2, orderItem.getQuantity());
        assertEquals(
                new BigDecimal("50000.00"),
                orderItem.getPriceAtPurchase()
        );
        assertEquals(savedOrder, orderItem.getOrder());
        assertEquals("PENDING", response.getStatus());
        assertEquals(
                new BigDecimal("100000.00"),
                response.getTotalAmount()
        );
        assertEquals("123 Main Street", response.getAddress());
        assertEquals("517501", response.getPincode());
        assertEquals(1, response.getItems().size());

        verify(stockService)
                .deductStock(product, 2);
        verify(cartRepository)
                .save(cart);
        assertTrue(cartItems.isEmpty());
    }


    @Test
    void placeOrder_shouldCalculateTotalForMultipleItems() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Product laptop = createProduct(
                101L,
                "Laptop",
                "50000.00"
        );
        Product mouse = createProduct(
                102L,
                "Mouse",
                "1000.00"
        );

        CartItem laptopItem = mock(CartItem.class);
        when(laptopItem.getProduct()).thenReturn(laptop);
        when(laptopItem.getQuantity()).thenReturn(2);
        CartItem mouseItem = mock(CartItem.class);
        when(mouseItem.getProduct()).thenReturn(mouse);
        when(mouseItem.getQuantity()).thenReturn(3);

        List<CartItem> cartItems =
                new ArrayList<>(List.of(laptopItem, mouseItem));
        Cart cart = mock(Cart.class);
        when(cart.getItems()).thenReturn(cartItems);

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        UserAddress address = createAddress(
                10L,
                "123 Main Street",
                "517501"
        );
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAddressId(10L);

        when(addressRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        OrderResponse response =
                orderService.placeOrder(request);
        assertEquals(
                new BigDecimal("103000.00"),
                response.getTotalAmount()
        );

        verify(stockService)
                .deductStock(laptop, 2);
        verify(stockService)
                .deductStock(mouse, 3);
        verify(stockService, times(2))
                .deductStock(any(Product.class), anyInt());
        verify(orderRepository)
                .save(any(Order.class));
        verify(cartRepository)
                .save(cart);
    }


    @Test
    void placeOrder_shouldThrowException_whenCartDoesNotExist() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAddressId(10L);
        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.placeOrder(request)
                );

        assertEquals(
                "Cart not found: 1",
                exception.getMessage()
        );
        verify(cartRepository)
                .findByUserId(1L);
        verifyNoInteractions(
                stockService,
                addressRepository,
                orderRepository
        );
    }


    @Test
    void placeOrder_shouldThrowException_whenCartIsEmpty() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Cart cart = mock(Cart.class);
        when(cart.getItems())
                .thenReturn(new ArrayList<>());
        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAddressId(10L);

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> orderService.placeOrder(request)
                );

        assertEquals(
                "Cannot place order with empty cart",
                exception.getMessage()
        );
        verifyNoInteractions(
                stockService,
                addressRepository,
                orderRepository
        );
    }


    @Test
    void placeOrder_shouldThrowException_whenAddressDoesNotExist() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Product product = createProduct(
                101L,
                "Laptop",
                "50000.00"
        );
        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getProduct()).thenReturn(product);
        when(cartItem.getQuantity()).thenReturn(1);

        Cart cart = mock(Cart.class);
        when(cart.getItems())
                .thenReturn(
                        new ArrayList<>(List.of(cartItem))
                );
        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAddressId(10L);
        when(addressRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.placeOrder(request)
                );

        assertEquals(
                "Address not found",
                exception.getMessage()
        );
        verify(stockService)
                .deductStock(product, 1);
        verify(addressRepository)
                .findByIdAndUserId(10L, 1L);
        verifyNoInteractions(orderRepository);
        verify(cartRepository, never())
                .save(any(Cart.class));
    }

    @Test
    void placeOrder_shouldDeductStockForEveryCartItem() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Product firstProduct =
                createProduct(
                        101L,
                        "Laptop",
                        "50000.00"
                );
        Product secondProduct =
                createProduct(
                        102L,
                        "Keyboard",
                        "2000.00"
                );

        CartItem firstItem = mock(CartItem.class);
        when(firstItem.getProduct()).thenReturn(firstProduct);
        when(firstItem.getQuantity()).thenReturn(2);

        CartItem secondItem = mock(CartItem.class);
        when(secondItem.getProduct()).thenReturn(secondProduct);
        when(secondItem.getQuantity()).thenReturn(3);

        Cart cart = mock(Cart.class);
        when(cart.getItems())
                .thenReturn(
                        new ArrayList<>(
                                List.of(firstItem, secondItem)
                        )
                );
        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        UserAddress address =
                createAddress(
                        10L,
                        "123 Main Street",
                        "517501"
                );

        PlaceOrderRequest request =
                new PlaceOrderRequest();
        request.setAddressId(10L);
        when(addressRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderService.placeOrder(request);
        verify(stockService)
                .deductStock(firstProduct, 2);
        verify(stockService)
                .deductStock(secondProduct, 3);
        verify(stockService, times(2))
                .deductStock(any(Product.class), anyInt());
    }

    @Test
    void getMyOrders_shouldReturnMappedOrdersForCurrentUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.CONFIRMED,
                "50000.00"
        );
        when(orderRepository.findByUserIdWithItems(1L))
                .thenReturn(List.of(order));

        List<OrderResponse> responses =
                orderService.getMyOrders();
        assertEquals(1, responses.size());

        OrderResponse response =
                responses.get(0);
        assertEquals(1001L, response.getId());
        assertEquals(
                "CONFIRMED",
                response.getStatus()
        );
        assertEquals(
                new BigDecimal("50000.00"),
                response.getTotalAmount()
        );
        verify(orderRepository)
                .findByUserIdWithItems(1L);
    }


    @Test
    void getMyOrders_shouldReturnEmptyList_whenUserHasNoOrders() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByUserIdWithItems(1L))
                .thenReturn(List.of());

        List<OrderResponse> responses =
                orderService.getMyOrders();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(orderRepository)
                .findByUserIdWithItems(1L);
    }

    @Test
    void getOrderById_shouldReturnOrder_whenOrderBelongsToCurrentUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.SHIPPED,
                "2500.00"
        );
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.of(order));

        OrderResponse response =
                orderService.getOrderById(1001L);
        assertEquals(
                1001L,
                response.getId()
        );
        assertEquals(
                "SHIPPED",
                response.getStatus()
        );
        assertEquals(
                new BigDecimal("2500.00"),
                response.getTotalAmount()
        );
        verify(orderRepository)
                .findByIdAndUserIdWithItems(
                        1001L,
                        1L
                );
    }


    @Test
    void getOrderById_shouldThrowException_whenOrderDoesNotBelongToUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.getOrderById(1001L)
                );
        assertEquals(
                "Order Not Found: 1001",
                exception.getMessage()
        );
        verify(orderRepository)
                .findByIdAndUserIdWithItems(
                        1001L,
                        1L
                );
    }

    @Test
    void cancelOrder_shouldMarkCancellationPendingAndInitiateRefund_whenOrderIsConfirmedAndPaymentSucceeded() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.CONFIRMED,
                "50000.00"
        );
        Payment payment = mock(Payment.class);
        when(payment.getStatus())
                .thenReturn(PaymentStatus.SUCCESS);
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderWithLock(order))
                .thenReturn(Optional.of(payment));
        when(orderRepository.save(order))
                .thenReturn(order);

        OrderResponse response =
                orderService.cancelOrder(1001L);
        assertEquals(
                OrderStatus.CANCELLATION_PENDING,
                order.getStatus()
        );
        assertEquals(
                "CANCELLATION_PENDING",
                response.getStatus()
        );
        verify(orderRepository)
                .save(order);
        verify(paymentRepository)
                .findByOrderWithLock(order);
        verify(paymentService)
                .initiateRefund(payment);
    }


    @Test
    void cancelOrder_shouldThrowException_whenOrderDoesNotExist() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.cancelOrder(1001L)
                );
        assertEquals(
                "Order Not Found: 1001",
                exception.getMessage()
        );
        verifyNoInteractions(
                paymentRepository,
                paymentService
        );
    }

    @Test
    void cancelOrder_shouldThrowException_whenOrderIsNotConfirmed() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.PENDING,
                "50000.00"
        );
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.of(order));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> orderService.cancelOrder(1001L)
                );
        assertEquals(
                "Only CONFIRMED orders can be cancelled",
                exception.getMessage()
        );
        verifyNoInteractions(
                paymentRepository,
                paymentService
        );
        verify(orderRepository, never())
                .save(any(Order.class));
    }

    @Test
    void cancelOrder_shouldThrowException_whenPaymentDoesNotExist() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.CONFIRMED,
                "50000.00"
        );
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderWithLock(order))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.cancelOrder(1001L)
                );
        assertEquals(
                "Payment not found for order: 1001",
                exception.getMessage()
        );
        verify(paymentRepository)
                .findByOrderWithLock(order);
        verify(paymentService, never())
                .initiateRefund(any(Payment.class));
        verify(orderRepository, never())
                .save(any(Order.class));
    }


    @Test
    void cancelOrder_shouldThrowException_whenPaymentStatusIsNotSuccess() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(util.getCurrentUser()).thenReturn(user);

        Order order = createOrder(
                1001L,
                user,
                OrderStatus.CONFIRMED,
                "50000.00"
        );
        Payment payment = mock(Payment.class);
        when(payment.getStatus())
                .thenReturn(PaymentStatus.FAILED);
        when(orderRepository.findByIdAndUserIdWithItems(
                1001L,
                1L
        )).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderWithLock(order))
                .thenReturn(Optional.of(payment));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> orderService.cancelOrder(1001L)
                );
        assertEquals(
                "Only successfully paid orders can be cancelled",
                exception.getMessage()
        );
        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );
        verify(paymentRepository)
                .findByOrderWithLock(order);
        verify(paymentService, never())
                .initiateRefund(any(Payment.class));
        verify(orderRepository, never())
                .save(any(Order.class));
    }

    @Test
    void updateStatus_shouldMovePendingOrderToConfirmed() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.PENDING,
                "50000.00"
        );
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order))
                .thenReturn(order);

        OrderResponse response =
                orderService.updateStatus(
                        1001L,
                        OrderStatus.CONFIRMED
                );
        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );
        assertEquals(
                "CONFIRMED",
                response.getStatus()
        );
        verify(orderRepository)
                .save(order);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateStatus_shouldCancelPendingOrderAndRestoreStock() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.PENDING,
                "50000.00"
        );
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order))
                .thenReturn(order);

        OrderResponse response =
                orderService.updateStatus(
                        1001L,
                        OrderStatus.CANCELLED
                );
        assertEquals(
                OrderStatus.CANCELLED,
                order.getStatus()
        );
        assertEquals(
                "CANCELLED",
                response.getStatus()
        );
        verify(inventoryService)
                .restoreStock(order);
        verify(orderRepository)
                .save(order);
    }


    @Test
    void updateStatus_shouldMoveConfirmedOrderToShipped() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.CONFIRMED,
                "50000.00"
        );
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order))
                .thenReturn(order);

        OrderResponse response =
                orderService.updateStatus(
                        1001L,
                        OrderStatus.SHIPPED
                );
        assertEquals(
                OrderStatus.SHIPPED,
                order.getStatus()
        );
        assertEquals(
                "SHIPPED",
                response.getStatus()
        );
        verify(orderRepository)
                .save(order);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateStatus_shouldMoveShippedOrderToDelivered() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.SHIPPED,
                "50000.00"
        );
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order))
                .thenReturn(order);

        OrderResponse response =
                orderService.updateStatus(
                        1001L,
                        OrderStatus.DELIVERED
                );
        assertEquals(
                OrderStatus.DELIVERED,
                order.getStatus()
        );
        assertEquals(
                "DELIVERED",
                response.getStatus()
        );
        verify(orderRepository)
                .save(order);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateStatus_shouldThrowException_whenTransitionIsInvalid() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.PENDING,
                "50000.00"
        );
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.of(order));

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> orderService.updateStatus(
                                1001L,
                                OrderStatus.SHIPPED
                        )
                );
        assertEquals(
                "Invalid order status transition from PENDING to SHIPPED",
                exception.getMessage()
        );
        assertEquals(
                OrderStatus.PENDING,
                order.getStatus()
        );
        verify(orderRepository, never())
                .save(any(Order.class));
        verifyNoInteractions(inventoryService);
    }


    @Test
    void updateStatus_shouldThrowException_whenOrderDoesNotExist() {
        when(orderRepository.findByIdWithItems(1001L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.updateStatus(
                                1001L,
                                OrderStatus.CONFIRMED
                        )
                );
        assertEquals(
                "Order not found",
                exception.getMessage()
        );
        verify(orderRepository)
                .findByIdWithItems(1001L);
        verify(orderRepository, never())
                .save(any(Order.class));
        verifyNoInteractions(inventoryService);
    }

    @Test
    void handleFailedPayment_shouldFailPaymentCancelOrderAndRestoreStock() {
        Order order = createOrder(
                1001L,
                null,
                OrderStatus.PENDING,
                "50000.00"
        );
        Payment payment = mock(Payment.class);
        when(payment.getOrder())
                .thenReturn(order);

        orderService.handleFailedPayment(payment);
        verify(payment)
                .setStatus(PaymentStatus.FAILED);
        verify(paymentRepository)
                .save(payment);
        assertEquals(
                OrderStatus.CANCELLED,
                order.getStatus()
        );
        verify(orderRepository)
                .save(order);
        verify(inventoryService)
                .restoreStock(order);
    }

    @Test
    void markPaymentAttemptFailed_shouldMarkPaymentAsFailedAndSaveIt() {
        Payment payment = mock(Payment.class);
        orderService.markPaymentAttemptFailed(payment);
        verify(payment)
                .setStatus(PaymentStatus.FAILED);
        verify(paymentRepository)
                .save(payment);
        verifyNoInteractions(
                orderRepository,
                inventoryService,
                paymentService
        );
    }

    private Product createProduct(
            Long id,
            String name,
            String price
    ) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .build();
    }

    private UserAddress createAddress(
            Long id,
            String addressLine1,
            String pincode
    ) {
        return UserAddress.builder()
                .id(id)
                .addressLine1(addressLine1)
                .pincode(pincode)
                .build();
    }

    private Order createOrder(
            Long id,
            User user,
            OrderStatus status,
            String totalAmount
    ) {
        Product product =
                createProduct(
                        101L,
                        "Test Product",
                        totalAmount
                );

        OrderItem orderItem =
                OrderItem.builder()
                        .product(product)
                        .quantity(1)
                        .priceAtPurchase(
                                new BigDecimal(totalAmount)
                        )
                        .build();

        UserAddress address =
                createAddress(
                        10L,
                        "123 Main Street",
                        "517501"
                );

        Order order =
                Order.builder()
                        .id(id)
                        .user(user)
                        .items(
                                new ArrayList<>(
                                        List.of(orderItem)
                                )
                        )
                        .userAddress(address)
                        .totalAmount(
                                new BigDecimal(totalAmount)
                        )
                        .status(status)
                        .build();

        orderItem.setOrder(order);
        return order;
    }
}
