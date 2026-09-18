package com.platform.ecommerce.inventory.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is THE test that matters for the flash-sale requirement: 100 items
 * in stock, 300 concurrent buyers each trying to buy 1, and we assert that
 * exactly 100 holds succeed and stock never goes negative - proving the
 * Lua script's atomicity holds up under real contention, not just in theory.
 *
 * Requires Docker (Testcontainers spins up a real Redis 7 instance).
 */
// Datasource/JPA/Flyway/Kafka are excluded here - this test exercises only
// the Redis Lua atomicity guarantee and shouldn't need a live Postgres/Kafka.
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
class InventoryLuaScriptRaceConditionTest {

    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void startRedis() {
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private InventoryLuaScript luaScript;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void hundredItemsUnderThreeHundredConcurrentBuyers_neverOversells() throws InterruptedException {
        String productId = UUID.randomUUID().toString();
        int initialStock = 100;
        int concurrentBuyers = 300;

        redisTemplate.opsForValue().set("stock:product:" + productId, initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(concurrentBuyers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentBuyers; i++) {
            String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    var outcome = luaScript.holdStock(productId, userId, 1);
                    if (outcome.result() == InventoryLuaScript.HoldResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Object remaining = redisTemplate.opsForValue().get("stock:product:" + productId);
        int remainingStock = Integer.parseInt(String.valueOf(remaining));

        assertEquals(initialStock, successCount.get(),
                "exactly as many holds should succeed as there was stock");
        assertEquals(concurrentBuyers - initialStock, failureCount.get());
        assertEquals(0, remainingStock, "stock must never go negative or be left inconsistent");
    }
}
