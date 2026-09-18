package com.platform.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkOrderStatus status;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    public enum BulkOrderStatus { PENDING_APPROVAL, APPROVED, REJECTED, EXPIRED }
}
