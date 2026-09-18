package com.platform.ecommerce.inventory.dto;

public record HoldResponse(
        boolean success,
        String status,       // SUCCESS | INSUFFICIENT_STOCK | ALREADY_HELD
        long remainingStock,
        String message
) {}
