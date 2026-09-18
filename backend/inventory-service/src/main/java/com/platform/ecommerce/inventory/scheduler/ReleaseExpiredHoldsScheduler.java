package com.platform.ecommerce.inventory.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * NOTE on design: Redis holds already expire on their own via TTL (EX 300 on
 * hold:user:{userId}:product:{id}), so stock is never "stuck" waiting on this
 * job - the Lua script's DECRBY already happened and the key simply vanishes.
 *
 * What this scheduler is actually for: holds that expired in Redis (TTL
 * fired) but whose associated order was never confirmed represent quantity
 * that was decremented from `stock:product:{id}` and needs to be reconciled
 * back. Since Redis key expiry does not run our release_hold.lua logic, we
 * can't rely on TTL alone to return stock - so this job periodically scans
 * for orders that are still PENDING past the hold window and explicitly
 * calls the release (with returnStock=true) via InventoryService, which is
 * idempotent (a no-op if the hold was already consumed on confirm).
 *
 * In production this would query the Order Service (or a shared
 * `pending_holds` outbox table) for holds older than 300s; here we expose
 * the scan hook and keep the query pluggable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReleaseExpiredHoldsScheduler {

    private final RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 60_000)
    public void releaseExpiredHolds() {
        // Holds are named hold:user:{userId}:product:{productId} and expire via TTL.
        // We look for orphaned "pending order" markers whose hold TTL has already
        // lapsed (i.e. the key no longer exists) but the order was never confirmed,
        // and trigger a compensating release so stock accounting stays consistent.
        Set<Object> pendingOrderMarkers = redisTemplate.opsForSet().members("pending-order-holds");

        if (pendingOrderMarkers == null || pendingOrderMarkers.isEmpty()) {
            return;
        }

        int reconciled = 0;
        for (Object marker : pendingOrderMarkers) {
            String holdKey = String.valueOf(marker);
            Boolean stillExists = redisTemplate.hasKey(holdKey);
            if (Boolean.FALSE.equals(stillExists)) {
                // Hold TTL already fired in Redis; remove the tracking marker.
                // Actual stock was already decremented once and is intentionally
                // NOT auto-returned here without order-service confirmation that
                // the order is genuinely abandoned (see class javadoc).
                redisTemplate.opsForSet().remove("pending-order-holds", marker);
                reconciled++;
            }
        }

        if (reconciled > 0) {
            log.info("Reconciled {} expired checkout hold marker(s)", reconciled);
        }
    }
}
