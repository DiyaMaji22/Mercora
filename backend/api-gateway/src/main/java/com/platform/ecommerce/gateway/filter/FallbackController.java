package com.platform.ecommerce.gateway.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/inventory")
    public Mono<ResponseEntity<Map<String, Object>>> inventoryFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Inventory service is temporarily unavailable",
                "message", "Please retry your request shortly"
        )));
    }
}
