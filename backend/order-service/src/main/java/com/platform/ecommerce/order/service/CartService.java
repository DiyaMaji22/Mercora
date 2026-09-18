package com.platform.ecommerce.order.service;

import com.platform.ecommerce.order.dto.CartDtos.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The cart itself is just a convenience/UX construct - it does NOT reserve
 * stock. Stock is only ever touched at checkout time via Inventory
 * Service's hold_stock Lua script (see OrderService.placeOrder). This keeps
 * "adding to cart" cheap and contention-free.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final Duration CART_TTL = Duration.ofDays(7);

    private String cartKey(UUID userId) {
        return "cart:user:" + userId;
    }

    public void addItem(UUID userId, UUID productId, int quantity) {
        String key = cartKey(userId);
        redisTemplate.opsForHash().increment(key, productId.toString(), quantity);
        redisTemplate.expire(key, CART_TTL);
    }

    public void removeItem(UUID userId, UUID productId) {
        redisTemplate.opsForHash().delete(cartKey(userId), productId.toString());
    }

    public void clear(UUID userId) {
        redisTemplate.delete(cartKey(userId));
    }

    public List<CartItem> getItems(UUID userId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(cartKey(userId));
        return raw.entrySet().stream()
                .map(e -> new CartItem(UUID.fromString(String.valueOf(e.getKey())),
                        Integer.parseInt(String.valueOf(e.getValue()))))
                .collect(Collectors.toList());
    }
}
