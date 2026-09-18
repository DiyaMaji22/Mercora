package com.platform.ecommerce.payment.dto;

import com.platform.ecommerce.payment.entity.Payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentDtos {

    /** Simplified webhook payload shape - a real gateway (Stripe, etc.) has its own schema. */
    public record WebhookPayload(
            @NotBlank String providerReference,
            @NotNull PaymentStatus status
    ) {}

    public record PaymentResponse(
            String id,
            String orderId,
            String status,
            String amount
    ) {}
}
