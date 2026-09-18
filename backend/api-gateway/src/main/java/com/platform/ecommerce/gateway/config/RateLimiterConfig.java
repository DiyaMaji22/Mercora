package com.platform.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the RequestRateLimiter's bucket key. Authenticated requests
     * (post-JWT-filter, X-User-Id set) are limited per user; anonymous
     * requests fall back to remote IP. The 10k req/sec ceiling from the
     * spec is enforced by the Redis token-bucket filter configured per
     * route in application.yml (redis-rate-limiter.replenishRate /
     * .burstCapacity), using this key to partition the bucket.
     */
    @Bean
    public KeyResolver rateLimiterKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
