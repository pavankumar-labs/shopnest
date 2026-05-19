package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.pavankumar.shopnestecommercebackend.dto.CategoryRequest;
import com.pavankumar.shopnestecommercebackend.dto.CategoryResponse;
import com.pavankumar.shopnestecommercebackend.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Categories management APIs")
public class CategoryController {
    private  final CategoryService categoryService;

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create
            (@Valid @RequestBody CategoryRequest request){
        CategoryResponse response=categoryService.createCategory(request);
        return  ResponseEntity.status(201).body(ApiResponse
                .success(response,"created new Category"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById
            (@PathVariable Long id){
        CategoryResponse response=categoryService.getByCategoryId(id);
        return ResponseEntity.ok(ApiResponse
                .success(response,"Category fetched successfully"));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update
            (@PathVariable Long id,@Valid @RequestBody CategoryRequest request){
        CategoryResponse response=categoryService.updateCategory(id,request);
        return ResponseEntity.ok(ApiResponse.
                success(response,"updated Category successfully"));
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAll(){
        List<CategoryResponse> responses=categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.
                success(responses,"All Categories fetched successfully"));
    }

}
