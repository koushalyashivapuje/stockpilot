package com.stockpilot.dto.order;

import com.stockpilot.entity.Order;
import com.stockpilot.entity.OrderItem;
import com.stockpilot.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        Long createdByUserId,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items
) {
    public record Item(Long id, Long productId, String productName, String sku, BigDecimal unitPrice,
                        Integer quantity, BigDecimal lineTotal) {
        public static Item from(OrderItem item) {
            return new Item(item.getId(), item.getProduct().getId(), item.getProductNameSnapshot(),
                    item.getSkuSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity(), item.getLineTotal());
        }
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream().map(Item::from).toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.getCreatedBy().getId(), order.getCreatedBy().getFullName(),
                order.getCreatedAt(), order.getUpdatedAt(), items);
    }
}
