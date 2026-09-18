package com.platform.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record HoldRequest(
        @NotNull UUID productId,
        @NotNull UUID userId,
        @Min(1) int quantity
) {}
