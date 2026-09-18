package com.platform.ecommerce.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors order-service's OrderPlacedEvent. Kept as a separate class here
 * (rather than a shared library module) to preserve service independence -
 * each service can evolve its event schema tolerance without a lockstep
 * shared-jar release. JSON field names must stay in sync; a schema registry
 * (e.g. Confluent Schema Registry with Avro) is the production-grade fix
 * for this and is a natural extension point.
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
}
