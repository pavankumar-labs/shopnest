package com.pavankumar.shopnestecommercebackend.exception;

import com.pavankumar.shopnestecommercebackend.dto.ApiResponse;
import com.razorpay.RazorpayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionalHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest
            (BadRequestException exception, HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error
                        (exception.getMessage(),request.getRequestURI()));
    }
    @ExceptionHandler(RazorpayException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadGateway
            (RazorpayException exception, HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.
                        error(exception.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest
            (SignatureVerificationException exception, HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error
                        (exception.getMessage(),request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden
            (AccessDeniedException exception, HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound
            (ResourceNotFoundException exception,HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error
                        (exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict
            (ResourceAlreadyExistsException exception,HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse
                        .error(exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict
            (InsufficientStockException exception,HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse
                        .error(exception.getMessage(), request.getRequestURI()));
    }


    @ExceptionHandler(UnauthorisedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden
            (UnauthorisedException exception,HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error
                        (exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest
            (MethodArgumentNotValidException exception,HttpServletRequest request){
        String message=exception.getBindingResult().getFieldErrors()
                .stream().map(error->error.getField()+":"+error.getDefaultMessage())
                .collect(Collectors.joining(","));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)

                .body(ApiResponse.error(message,request.getRequestURI()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalServer(
            Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", request.getRequestURI()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Invalid request body. Please check your JSON format.",
                        request.getRequestURI()));
    }




}
