package com.platform.ecommerce.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Validates JWTs at the edge before any request reaches a downstream
 * service. Public auth endpoints are excluded via a simple path prefix
 * check; everything else must carry a valid Bearer token. On success, the
 * filter forwards resolved identity as headers so downstream services
 * don't need to be internet-reachable / re-expose auth endpoints - though
 * per defense-in-depth, each service still independently re-validates the
 * JWT itself (see each service's JwtAuthFilter).
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/products/search",
            "/api/v1/inventory/hold",     // hold endpoint itself validates identity via body
            "/api/v1/inventory/stream",   // SSE stock updates - public, shoppers needn't be signed in to browse
            "/actuator/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        boolean isPublicGet = "GET".equals(exchange.getRequest().getMethod().name())
                && path.startsWith("/api/v1/products/");

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith) || isPublicGet) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        try {
            String token = authHeader.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", claims.get("userId", String.class))
                    .header("X-User-Email", claims.getSubject())
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -1; // run before routing
    }
}
