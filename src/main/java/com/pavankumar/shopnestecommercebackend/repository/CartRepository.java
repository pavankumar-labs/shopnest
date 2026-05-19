package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart,Long> {

    @Query("""
       select distinct c
       from Cart c
       left join fetch c.items i
       left join fetch i.product
       where c.user.id = :id
       """)
    Optional<Cart> findByUserIdWithItems(@Param("id") Long id);

    Optional<Cart> findByUserId(Long id);

}
