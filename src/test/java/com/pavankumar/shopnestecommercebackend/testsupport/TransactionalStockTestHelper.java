package com.pavankumar.shopnestecommercebackend.testsupport;

import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.service.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionalStockTestHelper {
    private final StockService stockService;

    @Transactional
    public void deductStockInNewTransaction(Product product, int quantity) {
        stockService.deductStock(product, quantity);
    }
}
