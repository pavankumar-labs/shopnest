package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.Payment;
import com.pavankumar.shopnestecommercebackend.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.razorpayOrderId=:orderId")
    Optional<Payment> findByRazorpayOrderIdWithLock(@Param("orderId") String razorpayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.order = :order
            """)
    Optional<Payment> findByOrderWithLock(
            @Param("order") Order order);

    List<Payment> findByStatus(PaymentStatus status);
}
