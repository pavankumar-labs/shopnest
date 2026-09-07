package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.repository.*;
import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import com.pavankumar.shopnestecommercebackend.dto.OrderItemResponse;
import com.pavankumar.shopnestecommercebackend.dto.OrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PlaceOrderRequest;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AuthUtil util;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;
    private  final StockService stockService;
    private final PaymentService paymentService;


    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User user = util.getCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException
                        ("Cart not found: " + user.getId()));
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException
                    ("Cannot place order with empty cart");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            stockService.deductStock(product, cartItem.getQuantity());
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase((product.getPrice()))
                    .build();
            BigDecimal subTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subTotal);
            orderItems.add(orderItem);
        }
        UserAddress address=addressRepository.findByIdAndUserId(request.getAddressId(),user.getId())
                .orElseThrow(()->new ResourceNotFoundException("Address not found"));
        Order order = Order.builder()
                .user(user)
                .items(orderItems)
                .userAddress(address)

                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();
        orderItems.forEach(orderItem -> orderItem.setOrder(order));
        Order savedOrder = orderRepository.save(order);
        cart.getItems().clear();
        cartRepository.save(cart);
        return mapToOrderResponse(savedOrder);
    }

    public List<OrderResponse> getMyOrders() {
        User user = util.getCurrentUser();
        List<Order> orders = orderRepository.findByUserIdWithItems(user.getId());
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long orderId) {
        User user = util.getCurrentUser();
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException
                        ("Order Not Found: " + orderId));
        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {

        User user = util.getCurrentUser();
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException
                        ("Order Not Found: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException(
                    "Only CONFIRMED orders can be cancelled");
        }


        Payment payment = paymentRepository
                .findByOrderWithLock(order)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Payment not found for order: " + orderId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException(
                    "Only successfully paid orders can be cancelled");
        }


        order.setStatus(OrderStatus.CANCELLATION_PENDING);
        Order savedOrder = orderRepository.save(order);

        paymentService.initiateRefund(payment);

        return mapToOrderResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found"));
        OrderStatus currentStatus = order.getStatus();

        boolean validTransition =
                (currentStatus == OrderStatus.PENDING
                        && (newStatus == OrderStatus.CONFIRMED
                        || newStatus == OrderStatus.CANCELLED))

                        || (currentStatus == OrderStatus.CONFIRMED
                        && newStatus == OrderStatus.SHIPPED)

                        || (currentStatus == OrderStatus.SHIPPED
                        && newStatus == OrderStatus.DELIVERED);

        if (!validTransition) {
            throw new BadRequestException(
                    "Invalid order status transition from "
                            + currentStatus + " to " + newStatus);
        }
        if (newStatus == OrderStatus.CANCELLED) {
            inventoryService.restoreStock(order);
        }
        order.setStatus(newStatus);
        return mapToOrderResponse(orderRepository.save(order));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponseList = order.getItems()
                .stream().map(orderItem -> OrderItemResponse.builder()
                        .productId(orderItem.getProduct().getId())
                        .productName(orderItem.getProduct().getName())
                        .quantity(orderItem.getQuantity())
                        .priceAtPurchase(orderItem.getPriceAtPurchase())
                        .subTotal(orderItem.getPriceAtPurchase()
                                .multiply(BigDecimal.valueOf(orderItem.getQuantity())))
                        .build()).collect(Collectors.toList());
        return OrderResponse.builder()
                .status(order.getStatus().name())
                .items(itemResponseList)
                .address(order.getUserAddress().getAddressLine1())
                .pincode(order.getUserAddress().getPincode())
                .totalAmount(order.getTotalAmount())
                .id(order.getId())
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Transactional
    public void handleFailedPayment(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        inventoryService.restoreStock(order);
    }

    @Transactional
    public void markPaymentAttemptFailed(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }


}
