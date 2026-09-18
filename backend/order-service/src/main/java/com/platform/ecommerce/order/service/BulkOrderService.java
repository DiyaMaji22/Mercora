package com.platform.ecommerce.order.service;

import com.platform.ecommerce.order.client.InventoryClient;
import com.platform.ecommerce.order.client.InventoryClient.HoldResult;
import com.platform.ecommerce.order.entity.BulkOrder;
import com.platform.ecommerce.order.entity.BulkOrder.BulkOrderStatus;
import com.platform.ecommerce.order.exception.BulkOrderNotFoundException;
import com.platform.ecommerce.order.exception.InsufficientStockException;
import com.platform.ecommerce.order.repository.BulkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bulk-order flow per spec: "Temporary lock (7200s) -> Admin approval
 * workflow -> release on expiry". This service owns that workflow;
 * Inventory Service only knows how to place/release the underlying Redis
 * lock (see InventoryClient.bulkLock / releaseBulkLock) and has no opinion
 * on approval process, which lives here instead.
 */
@Service
@RequiredArgsConstructor
public class BulkOrderService {

    private final InventoryClient inventoryClient;
    private final BulkOrderRepository bulkOrderRepository;

    @Transactional
    public BulkOrder requestBulkOrder(UUID userId, UUID productId, int quantity) {
        HoldResult result = inventoryClient.bulkLock(productId, userId, quantity);
        if (!result.success()) {
            throw new InsufficientStockException(productId, result.message());
        }

        BulkOrder bulkOrder = BulkOrder.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .status(BulkOrderStatus.PENDING_APPROVAL)
                .requestedAt(Instant.now())
                .build();

        return bulkOrderRepository.save(bulkOrder);
    }

    public List<BulkOrder> listPending() {
        return bulkOrderRepository.findByStatus(BulkOrderStatus.PENDING_APPROVAL);
    }

    public List<BulkOrder> listForUser(UUID userId) {
        return bulkOrderRepository.findByUserIdOrderByRequestedAtDesc(userId);
    }

    @Transactional
    public BulkOrder decide(UUID bulkOrderId, UUID adminId, boolean approve) {
        BulkOrder bulkOrder = bulkOrderRepository.findById(bulkOrderId)
                .orElseThrow(() -> new BulkOrderNotFoundException(bulkOrderId));

        if (bulkOrder.getStatus() != BulkOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Bulk order " + bulkOrderId + " is not pending approval (status: " + bulkOrder.getStatus() + ")");
        }

        // Release the Redis lock first - if this fails (e.g. Inventory
        // Service unreachable), the DB row stays PENDING_APPROVAL and the
        // admin can safely retry the decision rather than the two systems
        // drifting out of sync.
        inventoryClient.releaseBulkLock(bulkOrder.getProductId(), bulkOrder.getUserId(),
                bulkOrder.getQuantity(), approve);

        bulkOrder.setStatus(approve ? BulkOrderStatus.APPROVED : BulkOrderStatus.REJECTED);
        bulkOrder.setDecidedAt(Instant.now());
        bulkOrder.setDecidedBy(adminId);

        return bulkOrderRepository.save(bulkOrder);
    }
}
