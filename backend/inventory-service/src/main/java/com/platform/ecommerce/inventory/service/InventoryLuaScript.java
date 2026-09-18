package com.platform.ecommerce.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin, focused wrapper around the three inventory Lua scripts. Every method
 * here executes in a single round trip and is atomic on the Redis side
 * (Redis executes Lua scripts single-threaded, so there is no interleaving
 * between concurrent hold_stock / release_hold / bulk_lock calls for the
 * same keys - this is what prevents oversell during flash sales).
 */
@Component
@RequiredArgsConstructor
public class InventoryLuaScript {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> holdStockScript;
    private final DefaultRedisScript<Long> releaseHoldScript;
    private final DefaultRedisScript<List> bulkLockScript;

    public static final long CHECKOUT_HOLD_TTL_SECONDS = 300;   // 5 minutes
    public static final long BULK_LOCK_TTL_SECONDS = 7200;      // 2 hours

    public enum HoldResult { SUCCESS, INSUFFICIENT_STOCK, ALREADY_HELD }

    public record HoldOutcome(HoldResult result, long stockValue) {}

    /**
     * Atomically deducts stock and creates a 5-minute checkout hold for a
     * flash-sale purchase.
     */
    public HoldOutcome holdStock(String productId, String userId, int quantity) {
        String stockKey = "stock:product:" + productId;
        String holdKey = "hold:user:" + userId + ":product:" + productId;

        @SuppressWarnings("unchecked")
        List<Long> raw = redisTemplate.execute(
                holdStockScript,
                List.of(stockKey, holdKey),
                String.valueOf(quantity),
                String.valueOf(CHECKOUT_HOLD_TTL_SECONDS)
        );

        long code = raw.get(0);
        long value = raw.get(1);

        return switch ((int) code) {
            case 1 -> new HoldOutcome(HoldResult.SUCCESS, value);
            case -1 -> new HoldOutcome(HoldResult.ALREADY_HELD, value);
            default -> new HoldOutcome(HoldResult.INSUFFICIENT_STOCK, value);
        };
    }

    /** Releases a hold. If the order was cancelled or the hold expired, stock is returned. */
    public boolean releaseHold(String productId, String userId, int expectedQty, boolean returnStock) {
        String holdKey = "hold:user:" + userId + ":product:" + productId;
        String stockKey = "stock:product:" + productId;

        Long result = redisTemplate.execute(
                releaseHoldScript,
                List.of(holdKey, stockKey),
                String.valueOf(expectedQty),
                returnStock ? "RETURN" : "CONSUME"
        );

        return result != null && result == 1L;
    }

    /**
     * Releases a bulk lock following an admin approve/reject decision.
     * Reuses release_hold.lua - the script is agnostic to key naming, it
     * only cares about the held-quantity semantics (DEL the lock key;
     * INCRBY stock back only if returnStock is set) - so the same atomic
     * operation that services checkout-hold expiry also correctly services
     * bulk-lock approval (returnStock=false, stock stays deducted as part
     * of the now-confirmed bulk order) and rejection (returnStock=true).
     */
    public boolean releaseBulkLock(String productId, String userId, int expectedQty, boolean returnStock) {
        String lockKey = "bulk:lock:" + productId + ":" + userId;
        String stockKey = "stock:product:" + productId;

        Long result = redisTemplate.execute(
                releaseHoldScript,
                List.of(lockKey, stockKey),
                String.valueOf(expectedQty),
                returnStock ? "RETURN" : "CONSUME"
        );

        return result != null && result == 1L;
    }

    /** Places a 2-hour bulk-order lock pending admin approval. */
    public HoldOutcome bulkLock(String productId, String userId, int quantity) {
        String stockKey = "stock:product:" + productId;
        String lockKey = "bulk:lock:" + productId + ":" + userId;

        @SuppressWarnings("unchecked")
        List<Long> raw = redisTemplate.execute(
                bulkLockScript,
                List.of(stockKey, lockKey),
                String.valueOf(quantity),
                String.valueOf(BULK_LOCK_TTL_SECONDS)
        );

        long code = raw.get(0);
        long value = raw.get(1);

        return switch ((int) code) {
            case 1 -> new HoldOutcome(HoldResult.SUCCESS, value);
            case -1 -> new HoldOutcome(HoldResult.ALREADY_HELD, value);
            default -> new HoldOutcome(HoldResult.INSUFFICIENT_STOCK, value);
        };
    }
}
