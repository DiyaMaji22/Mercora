package com.platform.ecommerce.order.controller;

import com.platform.ecommerce.order.dto.CartDtos.AddToCartRequest;
import com.platform.ecommerce.order.dto.CartDtos.CartResponse;
import com.platform.ecommerce.order.dto.OrderDtos.PlaceOrderResponse;
import com.platform.ecommerce.order.service.CartService;
import com.platform.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final CartService cartService;
    private final OrderService orderService;

    // --- Cart ---

    @PostMapping("/cart/items")
    public ResponseEntity<Void> addToCart(Authentication auth, @Valid @RequestBody AddToCartRequest request) {
        cartService.addItem(userId(auth), request.productId(), request.quantity());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cart")
    public ResponseEntity<CartResponse> getCart(Authentication auth) {
        return ResponseEntity.ok(new CartResponse(cartService.getItems(userId(auth))));
    }

    @DeleteMapping("/cart/items/{productId}")
    public ResponseEntity<Void> removeFromCart(Authentication auth, @PathVariable UUID productId) {
        cartService.removeItem(userId(auth), productId);
        return ResponseEntity.noContent().build();
    }

    // --- Orders ---

    @PostMapping("/orders")
    public ResponseEntity<PlaceOrderResponse> placeOrder(Authentication auth) {
        PlaceOrderResponse response = orderService.placeOrder(userId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PlaceOrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @GetMapping("/orders/mine")
    public ResponseEntity<java.util.List<PlaceOrderResponse>> getMyOrders(Authentication auth) {
        return ResponseEntity.ok(orderService.getOrdersForUser(userId(auth)));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(Authentication auth, @PathVariable UUID orderId) {
        orderService.cancelOrder(orderId, userId(auth));
        return ResponseEntity.noContent().build();
    }

    /** userId is carried as a JWT claim; the gateway/user-service issue tokens with it. */
    private UUID userId(Authentication auth) {
        // In this simplified filter, principal name is the email; a real
        // deployment would carry userId as a claim decoded into the
        // Authentication details. Kept simple here for clarity.
        return UUID.fromString(auth.getDetails().toString());
    }
}
