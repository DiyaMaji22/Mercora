package com.platform.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_created_at", nullable = false)
    private Instant orderCreatedAt;

    @Column(name = "provider_reference")
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * Optimistic lock. Webhook deliveries can arrive out of order or be
     * retried by the provider; @Version ensures a stale/duplicate webhook
     * can't silently clobber a status transition that already happened
     * concurrently (e.g. SUCCEEDED being overwritten by a late-arriving
     * duplicate INITIATED update) - the write fails fast instead.
     */
    @Version
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum PaymentStatus { INITIATED, SUCCEEDED, FAILED, REFUNDED }
}
