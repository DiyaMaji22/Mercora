package com.platform.ecommerce.payment.service;

import com.platform.ecommerce.payment.entity.Payment;
import com.platform.ecommerce.payment.entity.Payment.PaymentStatus;
import com.platform.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;

    @Test
    void applyWebhookUpdate_neverRegressesFromSucceededToInitiated() {
        PaymentService service = new PaymentService(paymentRepository);

        Payment succeeded = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .providerReference("ref-123")
                .status(PaymentStatus.SUCCEEDED)
                .amount(new BigDecimal("50.00"))
                .version(3L)
                .build();

        when(paymentRepository.findByProviderReference("ref-123")).thenReturn(Optional.of(succeeded));

        Payment result = service.applyWebhookUpdate("ref-123", PaymentStatus.INITIATED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhookUpdate_appliesForwardTransition() {
        PaymentService service = new PaymentService(paymentRepository);

        Payment initiated = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .providerReference("ref-456")
                .status(PaymentStatus.INITIATED)
                .amount(new BigDecimal("50.00"))
                .version(1L)
                .build();

        when(paymentRepository.findByProviderReference("ref-456")).thenReturn(Optional.of(initiated));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.applyWebhookUpdate("ref-456", PaymentStatus.SUCCEEDED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(any(Payment.class));
    }
}
