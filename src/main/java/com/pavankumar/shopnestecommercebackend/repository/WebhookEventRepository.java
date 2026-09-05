package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent,Long> {

    boolean existsByEventId(String eventId);
}
