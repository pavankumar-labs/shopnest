package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.exception.InsufficientStockException;
import com.pavankumar.shopnestecommercebackend.model.Category;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.repository.CategoryRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import com.pavankumar.shopnestecommercebackend.testsupport.TransactionalStockTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceConcurrencyTest extends AbstractIntegrationTest {
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private TransactionalStockTestHelper stockTestHelper;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(
                TestData.uniqueCategory().build()
        );
    }

    private Product saveProductWithStock(int stock) {
        return productRepository.save(
                TestData.product(1, category)
                        .stock(stock)
                        .build()
        );
    }

    @Test
    void deductStock_singleThreadSufficientStock_deductsExactQuantity() {
        Product product = saveProductWithStock(10);
        stockTestHelper.deductStockInNewTransaction(product, 3);
        Product reloaded = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertEquals(7, reloaded.getStock());
    }

    @Test
    void deductStock_singleThreadInsufficientStock_throwsAndLeavesStockUnchanged() {
        Product product = saveProductWithStock(2);

        assertThrows(
                InsufficientStockException.class,
                () -> stockTestHelper.deductStockInNewTransaction(product, 5)
        );

        Product reloaded = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertEquals(2, reloaded.getStock());
    }

    @Test
    void deductStock_twoConcurrentThreadsLastUnit_exactlyOneSucceedsStockNeverNegative()
            throws Exception {
        Product product = saveProductWithStock(1);
        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Boolean>> futures =
                new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    stockTestHelper.deductStockInNewTransaction(
                            product,
                            1
                    );
                    return true;

                } catch (InsufficientStockException e) {
                    return false;
                }
            }));
        }

        startLatch.countDown();
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(10, TimeUnit.SECONDS)) {
                successCount++;
            }
        }

        executor.shutdown();
        assertEquals(
                1,
                successCount,
                "exactly one of the two concurrent deductions must succeed"
        );

        Product reloaded = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertEquals(
                0,
                reloaded.getStock(),
                "stock must settle at exactly 0, never negative"
        );
    }

    @Test
    void deductStock_fiveConcurrentThreadsLastUnit_onlyOneSucceedsAndStockNeverNegative()
            throws Exception {
        Product product = saveProductWithStock(1);
        int threadCount = 5;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Boolean>> futures =
                new ArrayList<>();

        AtomicInteger unexpectedFailures =
                new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();

                try {
                    stockTestHelper.deductStockInNewTransaction(
                            product,
                            1
                    );
                    return true;

                } catch (InsufficientStockException e) {

                    return false;

                } catch (Exception e) {
                    unexpectedFailures.incrementAndGet();
                    return false;
                }
            }));
        }

        startLatch.countDown();
        int successCount = 0;

        for (Future<Boolean> future : futures) {
            if (future.get(15, TimeUnit.SECONDS)) {
                successCount++;
            }
        }

        executor.shutdown();

        assertEquals(
                0,
                unexpectedFailures.get(),
                "every losing thread must fail with InsufficientStockException, not some other exception"
        );
        assertEquals(
                1,
                successCount,
                "under heavy contention, still exactly one deduction may succeed"
        );

        Product reloaded = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertEquals(
                0,
                reloaded.getStock(),
                "stock must never go negative even under 5-way contention"
        );
    }
}