package com.platform.ecommerce.order.repository;

import com.platform.ecommerce.order.entity.BulkOrder;
import com.platform.ecommerce.order.entity.BulkOrder.BulkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulkOrderRepository extends JpaRepository<BulkOrder, UUID> {
    List<BulkOrder> findByStatus(BulkOrderStatus status);
    List<BulkOrder> findByUserIdOrderByRequestedAtDesc(UUID userId);
}
