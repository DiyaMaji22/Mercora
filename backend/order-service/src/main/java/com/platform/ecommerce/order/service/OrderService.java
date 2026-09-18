package com.platform.ecommerce.order.service;

import com.platform.ecommerce.order.client.InventoryClient;
import com.platform.ecommerce.order.client.InventoryClient.HoldResult;
import com.platform.ecommerce.order.dto.CartDtos.CartItem;
import com.platform.ecommerce.order.dto.OrderDtos.LineItem;
import com.platform.ecommerce.order.dto.OrderDtos.PlaceOrderResponse;
import com.platform.ecommerce.order.entity.Order;
import com.platform.ecommerce.order.entity.Order.OrderStatus;
import com.platform.ecommerce.order.entity.OrderItem;
import com.platform.ecommerce.order.event.OrderPlacedEvent;
import com.platform.ecommerce.order.exception.InsufficientStockException;
import com.platform.ecommerce.order.exception.OrderNotFoundException;
import com.platform.ecommerce.order.repository.OrderItemRepository;
import com.platform.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Flash-sale checkout flow (per spec):
 *   1. (Gateway already applied the 10k req/sec rate limit before this is reached)
 *   2. Atomic Redis Lua hold per line item (deduct + 5-min hold)
 *   3. If any line item fails, roll back the holds already placed for this order
 *   4. Persist the PENDING order + items to PostgreSQL
 *   5. Publish OrderPlacedEvent to Kafka asynchronously (Payment Service picks it up)
 *   6. PostgreSQL commit finalizes the durable record; the scheduler in
 *      Inventory Service reconciles any hold that times out unconfirmed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final CartService cartService;
    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final ProductPriceClient productPriceClient;

    private static final String TOPIC = "order-events";
    private static final long HOLD_TTL_SECONDS = 300;

    @Transactional
    public PlaceOrderResponse placeOrder(UUID userId) {
        List<CartItem> cartItems = cartService.getItems(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        List<HeldItem> heldItems = new ArrayList<>();
        try {
            for (CartItem item : cartItems) {
                HoldResult result = inventoryClient.holdStock(item.productId(), userId, item.quantity());
                if (!result.success()) {
                    throw new InsufficientStockException(item.productId(), result.message());
                }
                heldItems.add(new HeldItem(item.productId(), item.quantity()));
            }
        } catch (RuntimeException ex) {
            // Roll back any holds already placed for this order before propagating.
            for (HeldItem held : heldItems) {
                inventoryClient.releaseHold(held.productId(), userId, held.quantity(), true);
            }
            throw ex;
        }

        List<LineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (HeldItem held : heldItems) {
            BigDecimal unitPrice = productPriceClient.getPrice(held.productId());
            lineItems.add(new LineItem(held.productId(), held.quantity(), unitPrice));
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(held.quantity())));
        }

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .createdAt(Instant.now())
                .build();
        Order saved = orderRepository.save(order);

        for (LineItem li : lineItems) {
            orderItemRepository.save(OrderItem.builder()
                    .orderId(saved.getId())
                    .orderCreatedAt(saved.getCreatedAt())
                    .productId(li.productId())
                    .quantity(li.quantity())
                    .unitPrice(li.unitPrice())
                    .build());
        }

        cartService.clear(userId);

        List<OrderPlacedEvent.Item> eventItems = lineItems.stream()
                .map(li -> new OrderPlacedEvent.Item(li.productId(), li.quantity(), li.unitPrice()))
                .toList();
        OrderPlacedEvent event = OrderPlacedEvent.of(saved.getId(), userId, total, eventItems);
        // keyed by orderId so all events for this order stay ordered on one partition
        kafkaTemplate.send(TOPIC, saved.getId().toString(), event);

        return new PlaceOrderResponse(
                saved.getId(), saved.getStatus(), total, lineItems,
                saved.getCreatedAt(), saved.getCreatedAt().plusSeconds(HOLD_TTL_SECONDS));
    }

    public PlaceOrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<LineItem> lineItems = items.stream()
                .map(i -> new LineItem(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new PlaceOrderResponse(
                order.getId(), order.getStatus(), order.getTotalAmount(), lineItems,
                order.getCreatedAt(), order.getCreatedAt().plusSeconds(HOLD_TTL_SECONDS));
    }

    public List<PlaceOrderResponse> getOrdersForUser(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> {
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    List<LineItem> lineItems = items.stream()
                            .map(i -> new LineItem(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                            .toList();
                    return new PlaceOrderResponse(
                            order.getId(), order.getStatus(), order.getTotalAmount(), lineItems,
                            order.getCreatedAt(), order.getCreatedAt().plusSeconds(HOLD_TTL_SECONDS));
                })
                .toList();
    }

    @Transactional
    public void cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryClient.releaseHold(item.getProductId(), userId, item.getQuantity(), true);
        }
    }

    private record HeldItem(UUID productId, int quantity) {}
}
