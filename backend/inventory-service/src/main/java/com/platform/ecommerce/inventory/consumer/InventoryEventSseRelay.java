package com.platform.ecommerce.inventory.consumer;

import com.platform.ecommerce.inventory.event.InventoryUpdateEvent;
import com.platform.ecommerce.inventory.service.InventoryEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * Consumes the same `inventory-events` topic Inventory Service itself
 * publishes to (self-loop by design - see class Javadoc on
 * InventoryEventBroadcaster) and relays each event to any browser
 * currently subscribed via /api/v1/inventory/stream/{productId}. This is
 * the last leg of the spec's "Retailer Update" flow: Postgres write ->
 * Kafka -> Redis sync -> SSE push.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryEventSseRelay {

    private final InventoryEventBroadcaster broadcaster;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "inventory-sse-relay-${random.uuid}", // unique per instance so every replica sees every event
            properties = {
                    "value.deserializer=" + "org.springframework.kafka.support.serializer.JsonDeserializer",
                    JsonDeserializer.TRUSTED_PACKAGES + "=com.platform.ecommerce.inventory.event",
                    JsonDeserializer.VALUE_DEFAULT_TYPE + "=com.platform.ecommerce.inventory.event.InventoryUpdateEvent"
            }
    )
    public void onInventoryEvent(InventoryUpdateEvent event) {
        broadcaster.broadcast(event.productId(), event);
    }
}
