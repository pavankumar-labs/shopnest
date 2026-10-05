package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import com.pavankumar.shopnestecommercebackend.model.*;
import com.pavankumar.shopnestecommercebackend.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryFindAbandonedOrdersTest extends AbstractIntegrationTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private Category category;
    private Product product;
    private User user;
    private UserAddress address;

    private void setUpSharedGraph() {
        category = entityManager.persist(TestData.uniqueCategory().build());
        product = entityManager.persist(TestData.product(1, category).build());
        user = entityManager.persist(TestData.user(1).build());
        address = entityManager.persist(TestData.address(user).build());
    }

    private Order persistOrderWithCreatedAt(OrderStatus status, LocalDateTime createdAt) {
        Order savedOrder = entityManager.persist(TestData.order(user, address, status).build());
        entityManager.persist(TestData.orderItem(savedOrder, product).build());
        entityManager.getEntityManager()
                .createQuery("update Order o set o.createdAt = :createdAt where o.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", savedOrder.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        return savedOrder;
    }

    @Test
    void findAbandonedOrders_matchingStatusAndOldEnough_isReturned() {
        setUpSharedGraph();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        Order oldPendingOrder = persistOrderWithCreatedAt(OrderStatus.PENDING, cutoff.minusMinutes(30));

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(OrderStatus.PENDING, cutoff);

        assertEquals(1, result.size());
        assertEquals(oldPendingOrder.getId(), result.get(0).getId());
    }

    @Test
    void findAbandonedOrders_pendingButTooRecent_isExcluded() {
        setUpSharedGraph();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        persistOrderWithCreatedAt(OrderStatus.PENDING, cutoff.plusMinutes(30));

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(OrderStatus.PENDING, cutoff);

        assertTrue(result.isEmpty(),
                "an order created after the cutoff must not be treated as abandoned");
    }

    @Test
    void findAbandonedOrders_oldEnoughButWrongStatus_isExcluded() {
        setUpSharedGraph();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        persistOrderWithCreatedAt(OrderStatus.CONFIRMED, cutoff.minusMinutes(30));

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(OrderStatus.PENDING, cutoff);

        assertTrue(result.isEmpty(),
                "a CONFIRMED order must never be picked up by the abandoned-PENDING-order query");
    }

    @Test
    void findAbandonedOrders_itemsAndProductAreEagerlyLoaded_noLazyInitializationException() {
        setUpSharedGraph();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        persistOrderWithCreatedAt(OrderStatus.PENDING, cutoff.minusMinutes(30));

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(OrderStatus.PENDING, cutoff);
        Order fetchedOrder = result.get(0);

        assertEquals(1, fetchedOrder.getItems().size());
        assertEquals(product.getName(), fetchedOrder.getItems().get(0).getProduct().getName());
    }

    @Test
    void findAbandonedOrders_noMatchingOrders_returnsEmptyListNotNull() {
        setUpSharedGraph();

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(
                OrderStatus.PENDING, LocalDateTime.now());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAbandonedOrders_orderWithMultipleItems_returnedOnceNotDuplicated() {
        setUpSharedGraph();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        Order savedOrder = entityManager.persist(TestData.order(user, address, OrderStatus.PENDING).build());
        entityManager.persist(TestData.orderItem(savedOrder, product).build());
        entityManager.persist(TestData.orderItem(savedOrder, product).build());
        entityManager.getEntityManager()
                .createQuery("update Order o set o.createdAt = :createdAt where o.id = :id")
                .setParameter("createdAt", cutoff.minusMinutes(30))
                .setParameter("id", savedOrder.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<Order> result = orderRepository.findAbandonedOrdersWithItems(OrderStatus.PENDING, cutoff);

        assertEquals(1, result.size(),
                "distinct must collapse the multi-row join back to one Order, not one per item");
        assertEquals(2, result.get(0).getItems().size());
    }
}