package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${scheduler.cutoff-minutes:10}")
    private int cutoffMinutes;


    @Transactional
    @Scheduled(fixedRateString ="${scheduler.rate-ms:90000}" )
    public void cancelAbandonedOrders(){

        LocalDateTime cutoff=LocalDateTime.now().minusMinutes(cutoffMinutes);

        List<Order> abandonedOrders=orderRepository
                .findAbandonedOrdersWithItems(OrderStatus.PENDING,cutoff);
        if (abandonedOrders.isEmpty()) {
            return;
        }

        for(Order order:abandonedOrders){
           Payment payment= paymentRepository.findByOrderWithLock(order).orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                continue;
            }

            if (payment.getStatus() == PaymentStatus.REFUNDED) {
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
