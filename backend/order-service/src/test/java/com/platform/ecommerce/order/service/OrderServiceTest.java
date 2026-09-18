package com.platform.ecommerce.order.service;

import com.platform.ecommerce.order.client.InventoryClient;
import com.platform.ecommerce.order.client.InventoryClient.HoldResult;
import com.platform.ecommerce.order.dto.CartDtos.CartItem;
import com.platform.ecommerce.order.event.OrderPlacedEvent;
import com.platform.ecommerce.order.exception.InsufficientStockException;
import com.platform.ecommerce.order.repository.OrderItemRepository;
import com.platform.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private CartService cartService;
    @Mock private InventoryClient inventoryClient;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    @Mock private ProductPriceClient productPriceClient;

    @Test
    void placeOrder_whenSecondItemOutOfStock_rollsBackFirstItemsHold() {
        OrderService orderService = new OrderService(
                cartService, inventoryClient, orderRepository, orderItemRepository,
                kafkaTemplate, productPriceClient);

        UUID userId = UUID.randomUUID();
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();

        when(cartService.getItems(userId)).thenReturn(List.of(
                new CartItem(productA, 1),
                new CartItem(productB, 1)
        ));

        when(inventoryClient.holdStock(eq(productA), eq(userId), eq(1)))
                .thenReturn(new HoldResult(true, "SUCCESS", 9, "held"));
        when(inventoryClient.holdStock(eq(productB), eq(userId), eq(1)))
                .thenReturn(new HoldResult(false, "INSUFFICIENT_STOCK", 0, "out of stock"));

        assertThatThrownBy(() -> orderService.placeOrder(userId))
                .isInstanceOf(InsufficientStockException.class);

        // The hold successfully placed for productA must be released since
        // the overall order failed - this is the rollback guarantee.
        verify(inventoryClient).releaseHold(productA, userId, 1, true);
        verify(inventoryClient, never()).releaseHold(eq(productB), any(), anyInt(), anyBoolean());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void placeOrder_withEmptyCart_throwsIllegalState() {
        OrderService orderService = new OrderService(
                cartService, inventoryClient, orderRepository, orderItemRepository,
                kafkaTemplate, productPriceClient);

        UUID userId = UUID.randomUUID();
        when(cartService.getItems(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.placeOrder(userId))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(inventoryClient);
    }
}
