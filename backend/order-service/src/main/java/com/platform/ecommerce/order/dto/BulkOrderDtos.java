package com.platform.ecommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class BulkOrderDtos {

    public record BulkOrderRequest(
            @NotNull UUID productId,
            @Min(1) int quantity
    ) {}
}
