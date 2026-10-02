package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderItem;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.repository.OrderRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product(Long id, int stock) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .price(BigDecimal.valueOf(100))
                .stock(stock)
                .build();
    }

    private OrderItem item(Product product, int quantity) {
        return OrderItem.builder()
                .product(product)
                .quantity(quantity)
                .priceAtPurchase(BigDecimal.valueOf(100))
                .build();
    }

    @Test
    void restoreStock_orderNotFound_throwsAndSavesNothing() {
        Order order = Order.builder().id(99L).build();
        when(orderRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());
        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> inventoryService.restoreStock(order));

        assertTrue(thrown.getMessage().contains("Order not found"));
        verify(productRepository, never()).save(any(Product.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void restoreStock_alreadyRestored_doesNothingAndSavesNothing() {
        Product product = product(1L, 10);
        Order lockedOrder = Order.builder()
                .id(1L)
                .stockRestored(true)
                .items(List.of(item(product, 2)))
                .build();

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(lockedOrder));
        inventoryService.restoreStock(Order.builder().id(1L).build());

        verify(productRepository, never()).save(any(Product.class));
        verify(orderRepository, never()).save(any(Order.class));
        assertEquals(10, product.getStock());
    }

    @Test
    void restoreStock_notYetRestored_restoresEachItemByItsOwnQuantity() {
        Product productA = product(1L, 10);
        Product productB = product(2L, 5);
        OrderItem itemA = item(productA, 3);
        OrderItem itemB = item(productB, 2);

        Order lockedOrder = Order.builder()
                .id(1L)
                .stockRestored(false)
                .items(List.of(itemA, itemB))
                .build();

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(lockedOrder));
        inventoryService.restoreStock(Order.builder().id(1L).build());

        assertEquals(13, productA.getStock());
        assertEquals(7, productB.getStock());
        verify(productRepository, times(1)).save(productA);
        verify(productRepository, times(1)).save(productB);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertTrue(orderCaptor.getValue().isStockRestored());
    }
}