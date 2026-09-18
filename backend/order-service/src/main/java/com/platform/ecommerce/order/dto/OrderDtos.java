package com.platform.ecommerce.order.dto;

import com.platform.ecommerce.order.entity.Order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderDtos {

    public record PlaceOrderResponse(
            UUID orderId,
            OrderStatus status,
            BigDecimal totalAmount,
            List<LineItem> items,
            Instant createdAt,
            Instant holdExpiresAt
    ) {}

    public record LineItem(UUID productId, int quantity, BigDecimal unitPrice) {}
}
