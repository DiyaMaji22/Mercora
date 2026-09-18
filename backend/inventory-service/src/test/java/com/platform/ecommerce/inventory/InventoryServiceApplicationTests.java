package com.platform.ecommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: verifies the Spring context loads with a real Postgres
 * Testcontainer wired in (Flyway migrations run against it), catching
 * config/wiring regressions early.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class InventoryServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Kafka/Redis autoconfig is tolerant of an unreachable broker/host at
        // context-startup for this smoke test; full integration flows are
        // covered by InventoryRaceConditionIT below, run against docker-compose.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Test
    void contextLoads() {
        // If the Spring context fails to start (bad bean wiring, missing
        // config, broken Flyway migration) this test fails.
    }
}
