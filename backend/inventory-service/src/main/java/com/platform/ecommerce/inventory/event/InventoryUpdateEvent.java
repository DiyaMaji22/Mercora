package com.platform.ecommerce.inventory.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the `inventory-events` Kafka topic whenever durable stock
 * changes (retailer update, order confirmation, hold release/expiry, bulk
 * approval). Order Service and any SSE-fronting gateway consume this to
 * push live StockIndicator updates to the frontend and to keep Redis in
 * sync with PostgreSQL as the source of truth.
 */
public record InventoryUpdateEvent(
        UUID eventId,
        UUID productId,
        Integer newAvailableStock,
        String changeReason,   // RETAILER_UPDATE | ORDER_CONFIRMED | HOLD_EXPIRED | BULK_APPROVED | BULK_REJECTED
        Instant occurredAt
) {
    public static InventoryUpdateEvent of(UUID productId, int newStock, String reason) {
        return new InventoryUpdateEvent(UUID.randomUUID(), productId, newStock, reason, Instant.now());
    }
}
