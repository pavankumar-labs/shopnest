package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.Order;
import com.pavankumar.shopnestecommercebackend.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {

    @Query("""
       select distinct o
       from Order o
       join fetch o.items i
       join fetch i.product
       join fetch o.userAddress
       where o.user.id = :id
       """)
    List<Order> findByUserIdWithItems(@Param("id") Long userId);

    @Query("select o from Order o join fetch o.items i join fetch i.product join fetch o.userAddress where o.id=:id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("select o from Order o join fetch o.items i join fetch i.product join fetch o.userAddress where o.user.id=:userId and o.id=:id")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") Long id,@Param("userId") Long userId);

    Optional<Order> findByIdAndUserId(Long id,Long userId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    @Query("select distinct o from Order o join fetch o.items i join fetch i.product where o.status = :status and o.createdAt < :cutoff")
    List<Order> findAbandonedOrdersWithItems(
            @Param("status") OrderStatus status,
            @Param("cutoff") LocalDateTime cutoff);

}
