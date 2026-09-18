package com.platform.ecommerce.payment.service;

import com.platform.ecommerce.payment.entity.Payment;
import com.platform.ecommerce.payment.entity.Payment.PaymentStatus;
import com.platform.ecommerce.payment.event.OrderPlacedEvent;
import com.platform.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Creates the INITIATED payment record for a newly-placed order. Called
     * from the Kafka listener before the payment gateway call is even
     * attempted, so we have a durable record to reconcile against
     * regardless of what happens next (gateway timeout, consumer crash,
     * etc.) - the offset is only committed after this returns successfully.
     */
    @Transactional
    public Payment initiate(OrderPlacedEvent event) {
        Payment payment = Payment.builder()
                .orderId(event.orderId())
                .orderCreatedAt(event.occurredAt())
                .status(PaymentStatus.INITIATED)
                .amount(event.totalAmount())
                .build();
        return paymentRepository.save(payment);
    }

    /**
     * Applies a webhook-delivered status update. Optimistic-lock retry
     * handles the case where two webhook deliveries for the same payment
     * race (provider retries are common); losing the race simply means
     * retrying against the now-current version rather than corrupting state.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public Payment applyWebhookUpdate(String providerReference, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findByProviderReference(providerReference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No payment found for provider reference " + providerReference));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED && newStatus == PaymentStatus.INITIATED) {
            // Out-of-order/duplicate webhook - never regress a terminal success state.
            log.debug("Ignoring stale webhook update for payment {}: already SUCCEEDED", payment.getId());
            return payment;
        }

        payment.setStatus(newStatus);
        return paymentRepository.save(payment); // @Version bump enforced here
    }

    public void linkProviderReference(UUID paymentId, String providerReference) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        payment.setProviderReference(providerReference);
        paymentRepository.save(payment);
    }
}
