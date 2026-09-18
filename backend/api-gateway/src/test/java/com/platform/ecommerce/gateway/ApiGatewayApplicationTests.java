package com.platform.ecommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies route definitions in application.yml parse correctly
        // and all beans (JwtAuthGlobalFilter, RateLimiterConfig,
        // FallbackController) wire up without error.
    }
}
