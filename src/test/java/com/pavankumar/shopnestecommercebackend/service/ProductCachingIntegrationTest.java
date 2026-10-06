package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.config.CacheConfig;
import com.pavankumar.shopnestecommercebackend.dto.ProductRequest;
import com.pavankumar.shopnestecommercebackend.dto.ProductResponse;
import com.pavankumar.shopnestecommercebackend.model.Category;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.repository.CategoryRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductCachingIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearProductCacheBeforeTest() {
        clearProductCache();
    }

    @AfterEach
    void clearProductCacheAfterTest() {
        clearProductCache();
    }

    private void clearProductCache() {
        Cache cache = getProductCache();

        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void getAllProducts_shouldCacheProductList() {
        Category category = createCategory();
        Product firstProduct =
                createProduct(category, "Cache Product One");
        Product secondProduct =
                createProduct(category, "Cache Product Two");

        List<ProductResponse> firstResult =
                productService.getAllProducts();

        assertNotNull(firstResult);
        assertTrue(
                firstResult.stream()
                        .anyMatch(product ->
                                product.getId().equals(firstProduct.getId())),
                "First product should be returned"
        );
        assertTrue(
                firstResult.stream()
                        .anyMatch(product ->
                                product.getId().equals(secondProduct.getId())),
                "Second product should be returned"
        );

        Cache cache = getProductCache();

        assertNotNull(cache);
        assertNotNull(
                cache.get("productList"),
                "Product list should be stored in the PRODUCTS cache"
        );

        Product thirdProduct =
                createProduct(category, "Cache Product Three");

        List<ProductResponse> secondResult =
                productService.getAllProducts();
        assertEquals(
                firstResult.size(),
                secondResult.size(),
                "Second read should use the cached product list"
        );
        assertFalse(
                secondResult.stream()
                        .anyMatch(product ->
                                product.getId().equals(thirdProduct.getId())),
                "New database data should not appear while the old list is cached"
        );
    }

    @Test
    void getProductById_shouldCacheIndividualProduct() {
        Category category = createCategory();
        Product product =
                createProduct(category, "Individual Cache Product");
        ProductResponse firstResult =
                productService.getProductById(product.getId());

        assertNotNull(firstResult);
        assertEquals(
                product.getId(),
                firstResult.getId()
        );
        assertEquals(
                "Individual Cache Product",
                firstResult.getName()
        );

        Cache cache = getProductCache();

        assertNotNull(cache);
        assertNotNull(
                cache.get(product.getId()),
                "Individual product should be stored in the PRODUCTS cache"
        );
        product.setName("Database Updated Name");
        productRepository.saveAndFlush(product);
        ProductResponse secondResult =
                productService.getProductById(product.getId());

        assertEquals(
                "Individual Cache Product",
                secondResult.getName(),
                "Second read should return the cached product"
        );
    }

    @Test
    void updateProduct_shouldEvictProductCache() {

        Category category = createCategory();

        Product product =
                createProduct(category, "Original Product Name");
        ProductResponse cachedProduct =
                productService.getProductById(product.getId());

        assertEquals(
                "Original Product Name",
                cachedProduct.getName()
        );

        Cache cache = getProductCache();
        assertNotNull(cache);
        assertNotNull(
                cache.get(product.getId()),
                "Product should be cached before update"
        );

        ProductRequest request = new ProductRequest(
                "Updated Product Name",
                "Updated description",
                BigDecimal.valueOf(1499),
                25,
                "updated-image.jpg",
                category.getId()
        );
        productService.updateProduct(
                product.getId(),
                request
        );

        assertNull(
                cache.get(product.getId()),
                "Updating a product must evict the cached product"
        );

        ProductResponse updatedProduct =
                productService.getProductById(product.getId());
        assertEquals(
                "Updated Product Name",
                updatedProduct.getName()
        );
        assertEquals(
                BigDecimal.valueOf(1499),
                updatedProduct.getPrice()
        );
        assertEquals(
                25,
                updatedProduct.getStock()
        );
    }

    @Test
    void createProduct_shouldEvictProductListCache() {
        Category category = createCategory();
        Product firstProduct =
                createProduct(category, "Existing Product");

        List<ProductResponse> firstResult =
                productService.getAllProducts();

        assertTrue(
                firstResult.stream()
                        .anyMatch(product ->
                                product.getId().equals(firstProduct.getId())),
                "Existing product should be returned"
        );

        Cache cache = getProductCache();
        assertNotNull(cache);

        assertNotNull(
                cache.get("productList"),
                "Product list should be cached before create"
        );

        ProductRequest request = new ProductRequest(
                "Newly Created Product",
                "Created after cache population",
                BigDecimal.valueOf(1999),
                15,
                "new-product.jpg",
                category.getId()
        );

        ProductResponse createdProduct =
                productService.createProduct(request);

        assertNotNull(createdProduct);
        assertEquals(
                "Newly Created Product",
                createdProduct.getName()
        );
        assertNull(
                cache.get("productList"),
                "Creating a product must evict the cached product list"
        );

        List<ProductResponse> secondResult =
                productService.getAllProducts();
        assertTrue(
                secondResult.stream()
                        .anyMatch(product ->
                                product.getId().equals(createdProduct.getId())),
                "New product must appear after the product-list cache is evicted"
        );
    }

    private Category createCategory() {
        return categoryRepository.save(
                TestData.uniqueCategory().build()
        );
    }

    private Product createProduct(
            Category category,
            String name
    ) {
        Product product = TestData.product(1, category)
                .name(name)
                .build();

        return productRepository.saveAndFlush(product);
    }

    private Cache getProductCache() {
        return cacheManager.getCache(CacheConfig.PRODUCTS);
    }
}