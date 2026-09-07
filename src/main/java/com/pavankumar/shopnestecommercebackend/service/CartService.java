package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.util.AuthUtil;
import com.pavankumar.shopnestecommercebackend.dto.AddToCartRequest;
import com.pavankumar.shopnestecommercebackend.dto.CartItemResponse;
import com.pavankumar.shopnestecommercebackend.dto.CartResponse;
import com.pavankumar.shopnestecommercebackend.exception.BadRequestException;
import com.pavankumar.shopnestecommercebackend.exception.ResourceNotFoundException;
import com.pavankumar.shopnestecommercebackend.exception.UnauthorisedException;
import com.pavankumar.shopnestecommercebackend.model.Cart;
import com.pavankumar.shopnestecommercebackend.model.CartItem;
import com.pavankumar.shopnestecommercebackend.model.Product;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.CartItemRepository;
import com.pavankumar.shopnestecommercebackend.repository.CartRepository;
import com.pavankumar.shopnestecommercebackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private  final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final AuthUtil util;

    public CartResponse getCart(){
        User user=util.getCurrentUser();
        Cart cart=cartRepository.findByUserIdWithItems(user.getId())
                .orElseGet(()->createCart(user));
        return mapToResponse(cart);
    }

    public CartResponse addToCart(AddToCartRequest request){
        User user=util.getCurrentUser();
        Product product=productRepository.findById(request.getProductId())
                .orElseThrow(()-> new ResourceNotFoundException
                        ("Product not found: "+request.getProductId()));
        if(product.getStock()< request.getQuantity()){
            throw new BadRequestException
                    ("Insufficient stock. available: " + product.getStock());
        }
        Cart cart=cartRepository.findByUserId(user.getId())
                .orElseGet(()->createCart(user));
        Optional<CartItem> item=cartItemRepository
                .findByCartIdAndProductId(cart.getId(),product.getId());
        if(item.isPresent()){
            CartItem  cartItem=item.get();
            int newQuantity=cartItem.getQuantity()+request.getQuantity();
            if(product.getStock()<newQuantity){
                throw new BadRequestException("Insufficient stock");
            }
            cartItem.setQuantity(newQuantity);
            cartItemRepository.save(cartItem);
        }
        else {
            CartItem cartItem=CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(cartItem);
        }
       return mapToResponse(cartRepository.findByUserIdWithItems(user.getId()).orElseThrow(() ->
               new ResourceNotFoundException("Cart is not found")));
    }

    public CartResponse removefromCart(Long cartItemId){
        User user=util.getCurrentUser();
        Cart cart=cartRepository.findByUserId(user.getId())
                .orElseThrow(()->new ResourceNotFoundException("Cart is Not Found"));
        CartItem cartItem=cartItemRepository.findById(cartItemId)
                .orElseThrow(()->new ResourceNotFoundException("CartItem Is not Found"));
        if(!(cartItem.getCart().getId().equals(cart.getId()))){
            throw new UnauthorisedException("You cannot remove items from another user's cart");
        }
        cartItemRepository.delete(cartItem);
        return mapToResponse(cartRepository.findByUserIdWithItems(user.getId()).orElseThrow(() ->
                new ResourceNotFoundException("Cart not found")));
    }
    public void clearCart(){
        User user=util.getCurrentUser();
        Cart cart=cartRepository.findByUserIdWithItems(user.getId())
                .orElseThrow(()->new ResourceNotFoundException
                        ("Cart not found"+user.getId()));
        cart.getItems().clear();
        cartRepository.save(cart);
    }


    private Cart createCart(User user){
          Cart cart= Cart.builder()
                  .user(user)
                  .items(new ArrayList<>())
                  .build();
          return  cartRepository.save(cart);
    }

    private CartResponse mapToResponse(Cart cart){
        List<CartItemResponse> cartResponseList=cart.getItems()
                .stream()
                .map(item-> CartItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productPrice(item.getProduct().getPrice())
                        .quantity(item.getQuantity())
                        .subTotal(item.getProduct().getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build()).toList();
        BigDecimal total=cartResponseList.stream()
                .map(CartItemResponse::getSubTotal).
                reduce(BigDecimal.ZERO,BigDecimal::add);
        return CartResponse.builder()
                .id(cart.getId())
                .cartItems(cartResponseList)
                .totalAmount(total)
                .totalItems(cartResponseList.size())
                .build();
    }

}
