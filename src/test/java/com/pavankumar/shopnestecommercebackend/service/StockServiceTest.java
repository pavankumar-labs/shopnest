package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.InsufficientStockException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockService stockService;

    private Product productWithStock(Long id, int stock) {
        return Product.builder()
                .id(id)
                .name("Test Product")
                .price(BigDecimal.valueOf(999))
                .stock(stock)
                .build();
    }

    @Test
    void deductStock_sufficientStock_decrementsAndSaves() {
        Product requested = productWithStock(1L, 10);
        Product fresh = productWithStock(1L, 10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(fresh));
        stockService.deductStock(requested, 3);

        verify(productRepository).findById(1L);
        ArgumentCaptor<Product> savedCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(savedCaptor.capture());
        assertEquals(7, savedCaptor.getValue().getStock());
    }

    @Test
    void deductStock_quantityExactlyEqualsStock_succeedsAndStockBecomesZero() {
        Product requested = productWithStock(1L, 5);
        Product fresh = productWithStock(1L, 5);

        when(productRepository.findById(1L)).thenReturn(Optional.of(fresh));
        stockService.deductStock(requested, 5);

        ArgumentCaptor<Product> savedCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(savedCaptor.capture());
        assertEquals(0, savedCaptor.getValue().getStock());
    }

    @Test
    void deductStock_insufficientStock_throwsAndNeverSaves() {
        Product requested = productWithStock(1L, 2);
        Product fresh = productWithStock(1L, 2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(fresh));
        InsufficientStockException thrown = assertThrows(InsufficientStockException.class,
                () -> stockService.deductStock(requested, 5));

        assertTrue(thrown.getMessage().contains("Stock is Unavailable"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deductStock_productNotFound_throwsAndNeverSaves() {
        Product requested = productWithStock(99L, 10);

        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> stockService.deductStock(requested, 3));

        assertTrue(thrown.getMessage().contains("Product not found"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void recover_anyOptimisticLockingFailure_alwaysThrowsInsufficientStockException() {
        Product product = productWithStock(1L, 10);
        OptimisticLockingFailureException conflict =
                new OptimisticLockingFailureException("version mismatch");

        InsufficientStockException thrown = assertThrows(InsufficientStockException.class,
                () -> stockService.recover(conflict, product, 3));

        assertEquals("Too many conflicts — please try again later", thrown.getMessage());
    }
}