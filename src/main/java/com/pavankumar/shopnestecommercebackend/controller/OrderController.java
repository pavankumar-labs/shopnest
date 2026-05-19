package com.pavankumar.shopnestecommercebackend.controller;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.pavankumar.shopnestecommercebackend.dto.OrderResponse;
import com.pavankumar.shopnestecommercebackend.dto.PlaceOrderRequest;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import com.pavankumar.shopnestecommercebackend.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Orders management APIs")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place
            (@Valid @RequestBody PlaceOrderRequest request){
        OrderResponse response=orderService.placeOrder(request);
        return ResponseEntity.status(201).body(ApiResponse.
                success(response,"placed Order successfully"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(){
        List<OrderResponse> responses=orderService.getMyOrders();
        return ResponseEntity.ok(ApiResponse.
                success(responses,"My orders fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById
            (@PathVariable Long id){
        OrderResponse response=orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse
                .success(response,"Order fetched"));
    }

 @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder
         (@PathVariable Long id){
        OrderResponse response=orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.
                success(response,"Order cancelled"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus
            (@PathVariable Long id, @RequestParam OrderStatus newStatus){
        OrderResponse response=orderService.updateStatus(id,newStatus);
        return ResponseEntity.ok(ApiResponse
                .success(response,"Order updated successfully"));
    }
}
