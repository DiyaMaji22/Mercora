package com.platform.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ProductDtos {

    public record CreateProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String description,
            String category,
            @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price
    ) {}

    public record UpdateProductRequest(
            String name,
            String description,
            String category,
            BigDecimal price
    ) {}

    public record ProductResponse(
            UUID id,
            String sku,
            String name,
            String description,
            String category,
            BigDecimal price,
            UUID retailerId,
            boolean active,
            Instant updatedAt
    ) {}
}
