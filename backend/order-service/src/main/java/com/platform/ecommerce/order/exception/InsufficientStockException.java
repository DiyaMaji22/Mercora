package com.platform.ecommerce.order.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID productId, String detail) {
        super("Insufficient stock for product " + productId + ": " + detail);
    }
}
