package com.platform.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_created_at", nullable = false)
    private Instant orderCreatedAt;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
}
