package com.platform.ecommerce.payment.repository;

import com.platform.ecommerce.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderReference(String providerReference);
}
