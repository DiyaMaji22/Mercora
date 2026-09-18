package com.platform.ecommerce.inventory.repository;

import com.platform.ecommerce.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    // Optimistic lock is enforced via the @Version column; this explicit
    // read-lock query is used by the retailer-update flow where we want to
    // fail fast with an OptimisticLockException on concurrent writes rather
    // than silently overwriting.
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);
}
