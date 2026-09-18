package com.platform.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseRequest(
        @NotNull UUID productId,
        @NotNull UUID userId,
        @Min(1) int quantity,
        boolean returnStock // true = cancelled/expired (stock returns), false = order confirmed (consumed)
) {}
