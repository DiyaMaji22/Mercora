package com.platform.ecommerce.order.controller;

import com.platform.ecommerce.order.dto.BulkOrderDtos.BulkOrderRequest;
import com.platform.ecommerce.order.entity.BulkOrder;
import com.platform.ecommerce.order.service.BulkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bulk-orders")
@RequiredArgsConstructor
public class BulkOrderController {

    private final BulkOrderService bulkOrderService;

    /** Bulk buyers request a 2-hour lock pending admin review. */
    @PostMapping
    @PreAuthorize("hasAnyRole('BULK_BUYER', 'ADMIN')")
    public ResponseEntity<BulkOrder> request(Authentication auth, @Valid @RequestBody BulkOrderRequest request) {
        BulkOrder created = bulkOrderService.requestBulkOrder(userId(auth), request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** The requesting user's own bulk order history. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('BULK_BUYER', 'ADMIN')")
    public ResponseEntity<List<BulkOrder>> mine(Authentication auth) {
        return ResponseEntity.ok(bulkOrderService.listForUser(userId(auth)));
    }

    /** Admin queue of requests awaiting a decision. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BulkOrder>> listPending(@RequestParam(required = false) String status) {
        // status filter is accepted for API-contract clarity (matches the
        // frontend's documented GET /api/v1/bulk-orders?status=PENDING_APPROVAL
        // call) but PENDING_APPROVAL is the only queue that matters operationally,
        // so that's what's returned regardless of the parameter's value for now.
        return ResponseEntity.ok(bulkOrderService.listPending());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkOrder> approve(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(bulkOrderService.decide(id, userId(auth), true));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkOrder> reject(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(bulkOrderService.decide(id, userId(auth), false));
    }

    private UUID userId(Authentication auth) {
        return UUID.fromString(auth.getDetails().toString());
    }
}
