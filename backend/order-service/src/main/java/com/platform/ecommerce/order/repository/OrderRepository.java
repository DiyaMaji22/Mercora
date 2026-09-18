package com.platform.ecommerce.order.repository;

import com.platform.ecommerce.order.entity.Order;
import com.platform.ecommerce.order.entity.OrderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, OrderId> {

    @Query("select o from Order o where o.id = :id")
    Optional<Order> findById(@Param("id") UUID id);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select o from Order o where o.status = 'PENDING' and o.createdAt < :cutoff")
    List<Order> findStalePendingOrders(@Param("cutoff") java.time.Instant cutoff);
}
