package com.pavankumar.shopnestecommercebackend;

import com.pavankumar.shopnestecommercebackend.dto.OrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PlaceOrderRequest;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.AddressRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import com.pavankumar.shopnestecommercebackend.service.EmailService;
import com.pavankumar.shopnestecommercebackend.service.InventoryService;
import com.pavankumar.shopnestecommercebackend.service.OrderService;
import com.pavankumar.shopnestecommercebackend.service.StockService;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;  // fake repository

    @Mock
    private AuthUtil util;  // fake auth

    @Mock
    private CartRepository cartRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StockService stockService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldReturnOrderList_whenUserHasOrders() {
        User mockUser = new User();
        mockUser.setId(1L);

        UserAddress mockAddress = new UserAddress();
        mockAddress.setAddressLine1("123 Street");
        mockAddress.setPincode("500001");

        Order mockOrder = new Order();
        mockOrder.setId(10L);
        mockOrder.setStatus(OrderStatus.PENDING);
        mockOrder.setTotalAmount(BigDecimal.valueOf(500));
        mockOrder.setItems(List.of());
        mockOrder.setUserAddress(mockAddress);

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(orderRepository.findByUserIdWithItems(1L)).thenReturn(List.of(mockOrder));

        List<OrderResponse> responses = orderService.getMyOrders();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("PENDING", responses.get(0).getStatus());

    }
    @Test
    void shouldReturnEmptyList_whenUserHasNoOrders(){
        User mockUser = new User();
        mockUser.setId(1L);

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(orderRepository.findByUserIdWithItems(1L)).thenReturn(List.of());

        List<OrderResponse> responses=orderService.getMyOrders();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void shouldThrowException_whenOrderNotFound() {
        User mockUser = new User();
        mockUser.setId(1L);
        when(util.getCurrentUser()).thenReturn(mockUser);
        when(orderRepository.findByIdAndUserIdWithItems
                (1L,1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,()->{
            orderService.cancelOrder(1L);
        });
    }

    @Test
    void shouldCancelOrder_whenStatusIsPending(){
        User mockUser = new User();
        mockUser.setId(1L);

        UserAddress mockAddress = new UserAddress();
        mockAddress.setAddressLine1("123 Street");
        mockAddress.setPincode("500001");

        Order mockOrder = new Order();
        mockOrder.setId(10L);
        mockOrder.setStatus(OrderStatus.PENDING);
        mockOrder.setTotalAmount(BigDecimal.valueOf(500));
        mockOrder.setItems(List.of());
        mockOrder.setUserAddress(mockAddress);

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(orderRepository.findByIdAndUserIdWithItems
                (mockOrder.getId(),1L)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(mockOrder)).thenReturn(mockOrder);

        OrderResponse orderResponse=orderService.cancelOrder(mockOrder.getId());
        assertNotNull(orderResponse);
        assertEquals("CANCELLED", orderResponse.getStatus());

    }
    @Test
    void shouldThrowException_whenOrderStatusIsNotPending(){
        User mockUser = new User();
        mockUser.setId(1L);

        UserAddress mockAddress = new UserAddress();
        mockAddress.setAddressLine1("123 Street");
        mockAddress.setPincode("500001");

        Order mockOrder = new Order();
        mockOrder.setId(10L);
        mockOrder.setStatus(OrderStatus.CONFIRMED);
        mockOrder.setTotalAmount(BigDecimal.valueOf(500));
        mockOrder.setItems(List.of());
        mockOrder.setUserAddress(mockAddress);

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(orderRepository.findByIdAndUserIdWithItems
                (mockOrder.getId(),1L)).thenReturn(Optional.of(mockOrder));
        assertThrows(BadRequestException.class, () -> {
            orderService.cancelOrder(mockOrder.getId());
        });

    }
    @Test
    void shouldThrowException_whenCartNotFound(){
        User mockUser = new User();
        mockUser.setId(1L);

        PlaceOrderRequest request=new PlaceOrderRequest();
        request.setAddressId(1L);

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(cartRepository.findByUserId(mockUser.getId()))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->{
            orderService.placeOrder(request);
        });

    }
    @Test
    void shouldThrowException_whenCartIsEmpty(){
        User mockUser = new User();
        mockUser.setId(1L);

        PlaceOrderRequest request=new PlaceOrderRequest();
        request.setAddressId(1L);
        Cart cart=new Cart();
        cart.setItems(List.of());


        when(util.getCurrentUser()).thenReturn(mockUser);
        when(cartRepository.findByUserId(mockUser.getId()))
                .thenReturn(Optional.of(cart));

        assertThrows(BadRequestException.class, () -> {
            orderService.placeOrder(request);
        });
    }

    @Test
    void shouldThrowException_whenCartEmptyAddress(){
        User mockUser = new User();
        mockUser.setId(1L);

        PlaceOrderRequest request=new PlaceOrderRequest();
        request.setAddressId(1L);
        Product product=new Product();
        product.setName("mrf bat");
        product.setId(1L);
        product.setPrice(BigDecimal.valueOf(555));
        product.setStock(4);

        CartItem cartItem=new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        Cart cart=new Cart();
        cart.setItems(List.of(cartItem));

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(cartRepository.findByUserId(mockUser.getId()))
                .thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L,1L))
                .thenReturn(Optional.empty());
        doNothing().when(stockService).deductStock(product, 2);
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.placeOrder(request);
        });
    }

    @Test
    void shouldPlaceOrder_whenAllDataIsValid(){
        User mockUser = new User();
        mockUser.setId(1L);

        PlaceOrderRequest request=new PlaceOrderRequest();
        request.setAddressId(1L);

        UserAddress address=new UserAddress();
        address.setId(1L);
        address.setAddressLine1("korlagunta ,Tirupati");
        address.setPincode("517501");
        address.setCity("tirupati");

        Product product=new Product();
        product.setName("mrf bat");
        product.setId(1L);
        product.setPrice(BigDecimal.valueOf(555));
        product.setStock(4);

        CartItem cartItem=new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        Cart cart=new Cart();
        cart.setItems(new ArrayList<>(List.of(cartItem)));

        Order order = new Order();
        order.setUser(mockUser);
        order.setUserAddress(address);
        order.setStatus(OrderStatus.PENDING);
        order.setItems(new ArrayList<>());
        order.setTotalAmount(BigDecimal.valueOf(1110));

        when(util.getCurrentUser()).thenReturn(mockUser);
        when(cartRepository.findByUserId(mockUser.getId()))
                .thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L,1L))
                .thenReturn(Optional.of(address));

        doNothing().when(stockService).deductStock(product, 2);
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse response=orderService.placeOrder(request);
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals(BigDecimal.valueOf(1110), response.getTotalAmount());

    }


}