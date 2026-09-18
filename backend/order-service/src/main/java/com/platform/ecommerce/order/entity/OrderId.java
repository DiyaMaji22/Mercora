package com.platform.ecommerce.order.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Composite key required because `orders` is partitioned by created_at. */
public class OrderId implements Serializable {
    private UUID id;
    private Instant createdAt;

    public OrderId() {}

    public OrderId(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderId orderId)) return false;
        return Objects.equals(id, orderId.id) && Objects.equals(createdAt, orderId.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
