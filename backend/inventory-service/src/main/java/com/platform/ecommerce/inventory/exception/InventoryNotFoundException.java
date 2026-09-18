package com.platform.ecommerce.inventory.exception;

import java.util.UUID;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(UUID productId) {
        super("No inventory record found for product " + productId);
    }
}
