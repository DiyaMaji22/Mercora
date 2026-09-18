package com.platform.ecommerce.payment.controller;

import com.platform.ecommerce.payment.dto.PaymentDtos.WebhookPayload;
import com.platform.ecommerce.payment.entity.Payment;
import com.platform.ecommerce.payment.repository.PaymentRepository;
import com.platform.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    /**
     * Payment gateway webhook endpoint. In production this would verify a
     * provider signature header (e.g. Stripe-Signature) before trusting the
     * payload - omitted here since there's no real gateway wired up, but
     * the verification step is a required addition before going live.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@Valid @RequestBody WebhookPayload payload) {
        paymentService.applyWebhookUpdate(payload.providerReference(), payload.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Payment>> getByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentRepository.findByOrderId(orderId));
    }
}
