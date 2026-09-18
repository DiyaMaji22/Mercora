package com.platform.ecommerce.inventory.config;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

/**
 * Redis configuration for the Inventory Service.
 *
 * Uses Lettuce (async, thread-safe, non-blocking) as the client. In a real
 * Redis Cluster deployment, replace RedisStandaloneConfiguration with
 * RedisClusterConfiguration and point at the cluster's seed nodes -
 * everything else (script loading, atomic Lua execution) is unchanged.
 *
 * Lua scripts are loaded once at startup as DefaultRedisScript beans so
 * Spring Data Redis can SCRIPT LOAD them (cached server-side, invoked by
 * SHA via EVALSHA) rather than re-sending source on every call.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public ClientResources clientResources() {
        return ClientResources.builder().build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .commandTimeout(Duration.ofSeconds(2))
                .clientResources(clientResources)
                .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public DefaultRedisScript<List> holdStockScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/hold_stock.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> releaseHoldScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/release_hold.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List> bulkLockScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/bulk_lock.lua"));
        script.setResultType(List.class);
        return script;
    }
}
