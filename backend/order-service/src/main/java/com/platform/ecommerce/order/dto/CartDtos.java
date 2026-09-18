package com.platform.ecommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public class CartDtos {

    public record CartItem(UUID productId, int quantity) implements Serializable {}

    public record AddToCartRequest(
            @NotNull UUID productId,
            @Min(1) int quantity
    ) {}

    public record CartResponse(List<CartItem> items) {}
}
