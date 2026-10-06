package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.repository.*;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceRestoreStockTest extends AbstractIntegrationTest {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    private Category category;
    private User user;
    private UserAddress address;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        category =
                categoryRepository.save(
                        TestData.category(1).build());
        user =
                userRepository.save(
                        TestData.user(1).build());
        address =
                addressRepository.save(
                        TestData.address(user).build());
    }

    private Order persistOrderWithItem(
            Product product,
            int quantity,
            boolean stockRestored) {

        Order order =
                orderRepository.save(
                        TestData.order(
                                        user,
                                        address,
                                        OrderStatus.CANCELLED)
                                .stockRestored(stockRestored)
                                .build());
        OrderItem item =
                TestData.orderItem(order, product)
                        .quantity(quantity)
                        .build();
        order.getItems().add(item);

        return orderRepository.save(order);
    }

    @Test
    void restoreStock_notYetRestored_incrementsStockAndMarksRestored() {
        Product product =
                productRepository.save(
                        TestData.product(1, category)
                                .stock(5)
                                .build());
        Order order =
                persistOrderWithItem(
                        product,
                        3,
                        false);

        inventoryService.restoreStock(order);
        Product reloadedProduct =
                productRepository
                        .findById(product.getId())
                        .orElseThrow();

        Order reloadedOrder =
                orderRepository
                        .findById(order.getId())
                        .orElseThrow();

        assertEquals(8, reloadedProduct.getStock());
        assertTrue(reloadedOrder.isStockRestored());
    }

    @Test
    void restoreStock_alreadyRestored_doesNotChangeStockAgain() {
        Product product =
                productRepository.save(
                        TestData.product(2, category)
                                .stock(5)
                                .build());
        Order order =
                persistOrderWithItem(
                        product,
                        3,
                        true);

        inventoryService.restoreStock(order);
        Product reloadedProduct =
                productRepository
                        .findById(product.getId())
                        .orElseThrow();

        assertEquals(
                5,
                reloadedProduct.getStock(),
                "stock must stay unchanged when stockRestored is already true");
    }

    @Test
    void restoreStock_orderNotFound_throwsAndTouchesNoProduct() {
        Product product =
                productRepository.save(
                        TestData.product(3, category)
                                .stock(5)
                                .build());
        Order phantomOrder =
                Order.builder()
                        .id(999999L)
                        .build();

        assertThrows(
                ResourceNotFoundException.class,
                () -> inventoryService.restoreStock(phantomOrder));

        Product reloadedProduct =
                productRepository
                        .findById(product.getId())
                        .orElseThrow();
        assertEquals(5, reloadedProduct.getStock());
    }

    @Test
    void restoreStock_twoConcurrentCallsSameOrder_stockIncrementedExactlyOnce()
            throws Exception {
        Product product =
                productRepository.save(
                        TestData.product(4, category)
                                .stock(5)
                                .build());
        Order order =
                persistOrderWithItem(
                        product,
                        3,
                        false);

        int threadCount = 2;
        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);
        AtomicInteger unexpectedFailures =
                new AtomicInteger(0);

        Future<?>[] futures =
                new Future<?>[threadCount];
        for (int i = 0; i < threadCount; i++) {

            futures[i] =
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            inventoryService.restoreStock(order);

                        } catch (Exception e) {
                            unexpectedFailures.incrementAndGet();
                        }
                    });
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(
                    15,
                    TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertEquals(
                0,
                unexpectedFailures.get(),
                "both calls should complete normally — "
                        + "the second should simply block then no-op, not throw");

        Product reloadedProduct =
                productRepository
                        .findById(product.getId())
                        .orElseThrow();
        assertEquals(
                8,
                reloadedProduct.getStock(),
                "stock must be restored exactly once, "
                        + "never twice, even under concurrent calls");
    }
}