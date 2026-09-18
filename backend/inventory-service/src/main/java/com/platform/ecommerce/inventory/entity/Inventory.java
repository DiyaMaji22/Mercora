package com.platform.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * System-of-record inventory row. Redis holds the *live, fast-path* stock
 * counter used during checkout; this table is the durable source of truth
 * that Redis is reconciled against (see InventorySyncService) and what
 * retailer stock updates write through via optimistic locking (@Version) to
 * avoid lost updates when multiple retailer/admin edits race.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
