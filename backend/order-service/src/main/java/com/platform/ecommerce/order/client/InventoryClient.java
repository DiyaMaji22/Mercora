package com.platform.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;
/**
 * Thin client to Inventory Service's Redis-Lua-backed hold/release
 * endpoints. Wrapped with Resilience4j: if Inventory Service is slow or
 * down, the circuit breaker trips fast rather than letting checkout
 * requests pile up and cascade failure back to the gateway.
 */
@Component
@RequiredArgsConstructor
public class InventoryClient {

    private final WebClient inventoryWebClient;

    public record HoldResult(boolean success, String status, long remainingStock, String message) {}

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "holdStockFallback")
    @Retry(name = "inventoryService")
    public HoldResult holdStock(UUID productId, UUID userId, int quantity) {
        return inventoryWebClient.post()
                .uri("/api/v1/inventory/hold")
                .bodyValue(new HoldRequestBody(productId, userId, quantity))
                .retrieve()
                .onStatus(status -> status.value() == 409, resp -> resp.bodyToMono(HoldResult.class).map(r ->
                        new InventoryConflictException(r.message())))
                .bodyToMono(HoldResult.class)
                .block();
    }

    @SuppressWarnings("unused")
    private HoldResult holdStockFallback(UUID productId, UUID userId, int quantity, Throwable t) {
        return new HoldResult(false, "SERVICE_UNAVAILABLE", 0,
                "Inventory service is temporarily unavailable, please try again shortly");
    }

    public void releaseHold(UUID productId, UUID userId, int quantity, boolean returnStock) {
        inventoryWebClient.post()
                .uri("/api/v1/inventory/release")
                .bodyValue(new ReleaseRequestBody(productId, userId, quantity, returnStock))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /** Places a 2-hour bulk lock pending admin approval. */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "bulkLockFallback")
    @Retry(name = "inventoryService")
    public HoldResult bulkLock(UUID productId, UUID userId, int quantity) {
        return inventoryWebClient.post()
                .uri("/api/v1/inventory/bulk-lock")
                .bodyValue(new HoldRequestBody(productId, userId, quantity))
                .retrieve()
                .bodyToMono(HoldResult.class)
                .block();
    }

    @SuppressWarnings("unused")
    private HoldResult bulkLockFallback(UUID productId, UUID userId, int quantity, Throwable t) {
        return new HoldResult(false, "SERVICE_UNAVAILABLE", 0,
                "Inventory service is temporarily unavailable, please try again shortly");
    }

    /** Admin decision on a bulk lock: approve (consume) or reject (return stock). */
    public void releaseBulkLock(UUID productId, UUID userId, int quantity, boolean approved) {
        inventoryWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/inventory/bulk-release")
                        .queryParam("approved", approved)
                        .build())
                .bodyValue(new ReleaseRequestBody(productId, userId, quantity, !approved))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private record HoldRequestBody(UUID productId, UUID userId, int quantity) {}
    private record ReleaseRequestBody(UUID productId, UUID userId, int quantity, boolean returnStock) {}

    public static class InventoryConflictException extends RuntimeException {
        public InventoryConflictException(String message) { super(message); }
    }
}
