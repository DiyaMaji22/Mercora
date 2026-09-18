package com.platform.ecommerce.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to `order-events` (3 partitions, keyed by orderId) once an
 * order has successfully placed its inventory holds. Payment Service
 * consumes this with manual offset commit to trigger payment processing;
 * committing the offset only after the payment attempt is durably recorded
 * (see payment-service's OrderEventConsumer) avoids losing an order if the
 * consumer crashes mid-processing.
 */
public record OrderPlacedEvent(
        UUID eventId,
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        List<Item> items,
        Instant occurredAt
) {
    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {}

    public static OrderPlacedEvent of(UUID orderId, UUID userId, BigDecimal total, List<Item> items) {
        return new OrderPlacedEvent(UUID.randomUUID(), orderId, userId, total, items, Instant.now());
    }
}
