package com.pavankumar.shopnestecommercebackend.event;

public record OrderCancelledEvent(
        Long orderId,
        String email,
        String userName){

}