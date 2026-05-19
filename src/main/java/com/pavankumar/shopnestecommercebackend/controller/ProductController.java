package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.pavankumar.shopnestecommercebackend.dto.PageResponse;
import com.pavankumar.shopnestecommercebackend.dto.ProductRequest;
import com.pavankumar.shopnestecommercebackend.dto.ProductResponse;
import com.pavankumar.shopnestecommercebackend.service.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Products management APIs")
public class ProductController {
    private final ProductService productService;

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create
            (@Valid @RequestBody ProductRequest request){
        ProductResponse response=productService.createProduct(request);
        return ResponseEntity.status(201).body(ApiResponse
                .success(response,"created Product successfully"));
    }

    @GetMapping
    @SecurityRequirements()
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAll(){
        List<ProductResponse> responses=productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse
                .success(responses,"All Products fetched"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById
            (@PathVariable Long id){
        ProductResponse response=productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse
                .success(response,"Product fetched successfully"));
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public  ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable Long id,@RequestBody @Valid ProductRequest request){
        ProductResponse response=productService.updateProduct(id,request);
        return ResponseEntity.ok(ApiResponse.success(response,"updated Product successfully"));
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getByCategory
            (@PathVariable Long categoryId){
        List<ProductResponse> responses=productService.getProductByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse
                .success(responses,"Products of category fetched "));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> search
            (@RequestParam String keyword){
        List<ProductResponse> responses=productService.searchProducts(keyword);
        return ResponseEntity.ok(ApiResponse
                .success(responses,"products matched search fetched"));
    }

    @GetMapping("/search/paginated")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProductsPaginated(
                      @RequestParam String keyword,
                      @RequestParam(defaultValue = "0") int page ,
                      @RequestParam(defaultValue = "10") int size){
        PageResponse<ProductResponse> response=productService
                                                         .searchProductsPaginated(keyword,page,size);
        return ResponseEntity.ok(ApiResponse.success(response,"products searched successfully"));
    }

    @GetMapping("/paginated")
    public  ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProductsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        PageResponse<ProductResponse> response=
                productService.getAllProductsPaginated(page,size);
        return ResponseEntity.ok(ApiResponse
                .success(response,"fetched products successfully"));
    }

    @GetMapping("/category/paginated/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> paginatedProductsByCategoryId(
            @PathVariable  Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        PageResponse<ProductResponse> response=productService.paginatedProductsByCategoryId(categoryId,page,size);
        return ResponseEntity.ok(ApiResponse.success(response,"products fetched by category"));
    }
}
