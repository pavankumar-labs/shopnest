package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
   private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional
    @Scheduled(fixedRate = 90000)
    public void cancelAbandonedOrders(){
        LocalDateTime cutoff=LocalDateTime.now().minusMinutes(7);

        List<Order> abandonedOrders=orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.PENDING,cutoff);
        if (abandonedOrders.isEmpty()) {
            return;
        }
        for(Order order:abandonedOrders){
           Payment payment= paymentRepository.findByOrder(order).orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                continue;
            }

            if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELLED) {
                continue;
            }

            orderService.handleFailedPayment(payment);
        }
        else {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            inventoryService.restoreStock(order);
        }
    }
    }
}
