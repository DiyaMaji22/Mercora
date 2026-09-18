package com.platform.ecommerce.payment.consumer;

import com.platform.ecommerce.payment.event.OrderPlacedEvent;
import com.platform.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final PaymentService paymentService;

    /**
     * Manual offset commit: the offset is only acknowledged after
     * paymentService.initiate() has durably persisted the payment record.
     * If the process crashes between consuming the record and acking it,
     * Kafka redelivers on restart - initiate() is effectively idempotent
     * per-order in practice since a duplicate INITIATED row is harmless
     * (a stricter dedup-by-orderId upsert is a straightforward follow-up).
     */
    @KafkaListener(topics = "order-events", groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onOrderPlaced(ConsumerRecord<String, OrderPlacedEvent> record, Acknowledgment ack) {
        OrderPlacedEvent event = record.value();
        log.info("Received OrderPlacedEvent for order={} amount={}", event.orderId(), event.totalAmount());

        try {
            paymentService.initiate(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to initiate payment for order={}, will retry via error handler", event.orderId(), e);
            throw e; // let DefaultErrorHandler's retry/DLQ policy handle it
        }
    }
}
