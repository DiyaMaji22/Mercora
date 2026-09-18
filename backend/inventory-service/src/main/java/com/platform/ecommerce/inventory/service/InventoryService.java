package com.platform.ecommerce.inventory.service;

import com.platform.ecommerce.inventory.dto.HoldRequest;
import com.platform.ecommerce.inventory.dto.HoldResponse;
import com.platform.ecommerce.inventory.dto.ReleaseRequest;
import com.platform.ecommerce.inventory.entity.Inventory;
import com.platform.ecommerce.inventory.event.InventoryUpdateEvent;
import com.platform.ecommerce.inventory.exception.InventoryNotFoundException;
import com.platform.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deadlock-prevention strategy for this service:
 *
 *  1. The Redis Lua scripts (hold_stock / release_hold / bulk_lock) are the
 *     single-threaded, atomic fast path used under contention (flash sales).
 *     They never call out to PostgreSQL inline, so there's no cross-store
 *     lock ordering to worry about there.
 *  2. PostgreSQL writes (retailer stock corrections, durable reconciliation)
 *     use optimistic locking (@Version) instead of row locks, so a slow
 *     writer never blocks others - it just retries on
 *     ObjectOptimisticLockingFailureException.
 *  3. Redis and PostgreSQL are kept in sync *asynchronously* via Kafka
 *     (`inventory-events`), never synchronously inside the hot path. This
 *     removes the classic distributed-deadlock shape where a request holds
 *     a Redis lock while waiting on a DB lock (or vice versa).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryLuaScript luaScript;
    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, InventoryUpdateEvent> kafkaTemplate;

    private static final String TOPIC = "inventory-events";

    public HoldResponse holdStock(HoldRequest request) {
        var outcome = luaScript.holdStock(
                request.productId().toString(), request.userId().toString(), request.quantity());

        return switch (outcome.result()) {
            case SUCCESS -> new HoldResponse(true, "SUCCESS", outcome.stockValue(),
                    "Hold placed for " + request.quantity() + " unit(s), expires in 300s");
            case ALREADY_HELD -> new HoldResponse(false, "ALREADY_HELD", outcome.stockValue(),
                    "An active hold already exists for this user/product");
            case INSUFFICIENT_STOCK -> new HoldResponse(false, "INSUFFICIENT_STOCK", outcome.stockValue(),
                    "Only " + outcome.stockValue() + " unit(s) available");
        };
    }

    public HoldResponse bulkLock(HoldRequest request) {
        var outcome = luaScript.bulkLock(
                request.productId().toString(), request.userId().toString(), request.quantity());

        return switch (outcome.result()) {
            case SUCCESS -> new HoldResponse(true, "SUCCESS", outcome.stockValue(),
                    "Bulk lock placed, pending admin approval, expires in 2h");
            case ALREADY_HELD -> new HoldResponse(false, "ALREADY_HELD", outcome.stockValue(),
                    "An active bulk lock already exists for this user/product");
            case INSUFFICIENT_STOCK -> new HoldResponse(false, "INSUFFICIENT_STOCK", outcome.stockValue(),
                    "Only " + outcome.stockValue() + " unit(s) available");
        };
    }

    /**
     * Releases a hold. Called by: the order flow on confirm/cancel, and the
     * scheduled expiry sweeper (ReleaseExpiredHoldsScheduler) for holds that
     * simply timed out.
     */
    public void releaseHold(ReleaseRequest request) {
        boolean released = luaScript.releaseHold(
                request.productId().toString(), request.userId().toString(),
                request.quantity(), request.returnStock());

        if (released && request.returnStock()) {
            publishInventoryEvent(request.productId(), "HOLD_EXPIRED");
        } else if (released) {
            publishInventoryEvent(request.productId(), "ORDER_CONFIRMED");
        } else {
            log.debug("Release no-op: hold for product={} user={} already cleared",
                    request.productId(), request.userId());
        }
    }

    /**
     * Releases a bulk lock following an admin decision. approved=true
     * consumes the lock (stock stays deducted, the bulk order proceeds to
     * fulfillment); approved=false returns the held quantity to live stock.
     */
    public void releaseBulkLock(ReleaseRequest request, boolean approved) {
        boolean released = luaScript.releaseBulkLock(
                request.productId().toString(), request.userId().toString(),
                request.quantity(), !approved);

        if (released) {
            publishInventoryEvent(request.productId(), approved ? "BULK_APPROVED" : "BULK_REJECTED");
        } else {
            log.debug("Bulk release no-op: lock for product={} user={} already cleared",
                    request.productId(), request.userId());
        }
    }

    /**
     * Retailer stock correction flow: PostgreSQL optimistic lock -> Kafka
     * event -> (async) Redis resync -> SSE push, per the "Retailer Update"
     * flow in the spec. Retries a bounded number of times on lock conflicts
     * rather than blocking other writers.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public Inventory applyRetailerStockUpdate(UUID productId, int newAvailableStock) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        inventory.setAvailableStock(newAvailableStock);
        Inventory saved = inventoryRepository.save(inventory); // @Version bump enforced here

        publishInventoryEvent(productId, "RETAILER_UPDATE");
        return saved;
    }

    private void publishInventoryEvent(UUID productId, String reason) {
        Inventory current = inventoryRepository.findByProductId(productId).orElse(null);
        int stock = current != null ? current.getAvailableStock() : 0;

        InventoryUpdateEvent event = InventoryUpdateEvent.of(productId, stock, reason);
        // keyed by productId so all events for a product land on the same
        // partition and are consumed in order
        kafkaTemplate.send(TOPIC, productId.toString(), event);
    }
}
