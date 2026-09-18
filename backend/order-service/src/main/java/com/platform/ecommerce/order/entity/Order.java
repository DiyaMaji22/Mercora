package com.platform.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps onto the `orders` table, which is RANGE-partitioned by created_at
 * (see V3__partition_orders.sql owned by inventory-service's migrations).
 * The composite primary key (id, created_at) is required by PostgreSQL for
 * any partitioned table's PK/FK to include the partition key.
 */
@Entity
@Table(name = "orders")
@IdClass(OrderId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum OrderStatus { PENDING, CONFIRMED, CANCELLED, EXPIRED, FULFILLED }
}
