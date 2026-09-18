package com.platform.ecommerce.order.exception;

import java.util.UUID;

public class BulkOrderNotFoundException extends RuntimeException {
    public BulkOrderNotFoundException(UUID id) {
        super("Bulk order not found: " + id);
    }
}
