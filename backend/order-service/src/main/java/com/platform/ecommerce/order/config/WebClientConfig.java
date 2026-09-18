package com.platform.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${services.inventory.base-url:http://localhost:8082}")
    private String inventoryBaseUrl;

    @Bean
    public WebClient inventoryWebClient() {
        return WebClient.builder()
                .baseUrl(inventoryBaseUrl)
                .build();
    }

    @Value("${services.product.base-url:http://localhost:8083}")
    private String productBaseUrl;

    @Bean
    public WebClient productWebClient() {
        return WebClient.builder()
                .baseUrl(productBaseUrl)
                .build();
    }
}
