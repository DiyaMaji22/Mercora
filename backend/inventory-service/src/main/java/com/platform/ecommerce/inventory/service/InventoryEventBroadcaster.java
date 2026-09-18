package com.platform.ecommerce.inventory.service;

import com.platform.ecommerce.inventory.event.InventoryUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-product reactive sinks replacing the MVC SseEmitter registry.
 * A single Netty event-loop thread services any number of concurrent SSE
 * connections; no Tomcat thread is held per subscriber.
 *
 * Sinks use MULTICAST with buffer-256 so a slow subscriber does not block
 * fast publishers. Events overflow silently per the onBackpressureBuffer
 * DROP policy when the buffer fills (acceptable for live stock tickers).
 *
 * Multi-replica: every instance consumes the full inventory-events topic
 * via a unique consumer-group suffix (InventoryEventSseRelay), broadcasting
 * to whichever clients connected to that replica.
 */
@Component
@Slf4j
public class InventoryEventBroadcaster {

    private final Map<UUID, Sinks.Many<InventoryUpdateEvent>> sinksByProduct = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<InventoryUpdateEvent>> subscribe(UUID productId) {
        Sinks.Many<InventoryUpdateEvent> sink = sinksByProduct
                .computeIfAbsent(productId,
                        k -> Sinks.many().multicast().onBackpressureBuffer(256, false));

        Flux<ServerSentEvent<InventoryUpdateEvent>> events = sink.asFlux()
                .map(event -> ServerSentEvent.<InventoryUpdateEvent>builder()
                        .id(event.eventId().toString())
                        .event(event.changeReason())
                        .data(event)
                        .build());

        Flux<ServerSentEvent<InventoryUpdateEvent>> heartbeat = Flux
                .interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<InventoryUpdateEvent>builder()
                        .comment("keepalive")
                        .build());

        return Flux.merge(events, heartbeat)
                .doFinally(signal -> {
                    Sinks.Many<InventoryUpdateEvent> current = sinksByProduct.get(productId);
                    if (current != null && current.currentSubscriberCount() == 0) {
                        sinksByProduct.remove(productId, current);
                    }
                });
    }

    public void broadcast(UUID productId, InventoryUpdateEvent event) {
        Sinks.Many<InventoryUpdateEvent> sink = sinksByProduct.get(productId);
        if (sink == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("SSE emit dropped for product {}: {}", productId, result);
        }
    }
}
