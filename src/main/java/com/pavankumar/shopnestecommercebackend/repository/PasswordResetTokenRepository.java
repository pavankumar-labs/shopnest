package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.PasswordResetToken;
import com.pavankumar.shopnestecommercebackend.model.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {


    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :user and t.used = false")
    void deleteAllUnusedByUser(@Param("user") User user);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
