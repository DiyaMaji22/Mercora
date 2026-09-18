package com.platform.ecommerce.inventory.controller;

import com.platform.ecommerce.inventory.dto.HoldRequest;
import com.platform.ecommerce.inventory.dto.HoldResponse;
import com.platform.ecommerce.inventory.dto.ReleaseRequest;
import com.platform.ecommerce.inventory.entity.Inventory;
import com.platform.ecommerce.inventory.service.InventoryEventBroadcaster;
import com.platform.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.platform.ecommerce.inventory.event.InventoryUpdateEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryEventBroadcaster broadcaster;

    /** Flash-sale checkout: atomic Redis deduct + 5-min hold. */
    @PostMapping("/hold")
    public ResponseEntity<HoldResponse> hold(@Valid @RequestBody HoldRequest request) {
        HoldResponse response = inventoryService.holdStock(request);
        HttpStatus status = response.success() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    /** Bulk buyer flow: 2-hour lock pending admin approval. */
    @PostMapping("/bulk-lock")
    public ResponseEntity<HoldResponse> bulkLock(@Valid @RequestBody HoldRequest request) {
        HoldResponse response = inventoryService.bulkLock(request);
        HttpStatus status = response.success() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    /** Release a hold - order confirmed (consume) or cancelled/expired (return stock). */
    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody ReleaseRequest request) {
        inventoryService.releaseHold(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Admin decision on a bulk lock: approve (consume - stock stays
     * deducted) or reject (return stock). Called by order-service's
     * BulkOrderService after it records the decision in the bulk_orders
     * table, so this endpoint trusts its caller rather than re-checking
     * admin identity itself - order-service already enforced
     * @PreAuthorize("hasRole('ADMIN')") before reaching here.
     */
    @PostMapping("/bulk-release")
    public ResponseEntity<Void> bulkRelease(
            @Valid @RequestBody ReleaseRequest request,
            @RequestParam boolean approved) {
        inventoryService.releaseBulkLock(request, approved);
        return ResponseEntity.noContent().build();
    }

    /** Retailer/admin durable stock correction: PostgreSQL optimistic lock + Kafka + Redis resync. */
    @PutMapping("/{productId}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Inventory> updateStock(
            @PathVariable UUID productId,
            @RequestParam int quantity) {
        Inventory updated = inventoryService.applyRetailerStockUpdate(productId, quantity);
        return ResponseEntity.ok(updated);
    }

    /**
     * SSE stream for live stock updates on a single product, consumed by
     * the frontend's StockIndicator component. Relays events published to
     * `inventory-events` (see InventoryEventSseRelay) - the last leg of the
     * retailer-update flow: Postgres write -> Kafka -> Redis sync -> here.
     */
    @GetMapping(value = "/stream/{productId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InventoryUpdateEvent>> stream(@PathVariable UUID productId) {
        return broadcaster.subscribe(productId);
    }
}
