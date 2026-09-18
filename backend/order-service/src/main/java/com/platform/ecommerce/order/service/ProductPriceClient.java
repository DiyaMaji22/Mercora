package com.platform.ecommerce.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductPriceClient {

    private final WebClient productWebClient;

    public BigDecimal getPrice(UUID productId) {
        Map<?, ?> product = productWebClient.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (product == null || product.get("price") == null) {
            throw new IllegalStateException("Could not resolve price for product " + productId);
        }
        return new BigDecimal(String.valueOf(product.get("price")));
    }
}
