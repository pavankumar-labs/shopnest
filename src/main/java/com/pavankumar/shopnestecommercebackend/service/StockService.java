package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.exception.InsufficientStockException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository productRepository;

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductStock(Product product, int quantity) {
        Product freshProduct = productRepository
                .findById(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found: " + product.getId()));
        if (freshProduct.getStock() < quantity) {
            throw new InsufficientStockException
                    ("Stock is Unavailable: " + product.getName());
        }
        freshProduct.setStock((freshProduct.getStock()) - quantity);
        productRepository.save(freshProduct);
    }

    @Recover
    public void recover(OptimisticLockingFailureException e,Product product, int quantity) {
        throw new InsufficientStockException(
                "Too many conflicts — please try again later");
    }
}
