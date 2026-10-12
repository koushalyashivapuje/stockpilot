package com.stockpilot.dto.order;

import com.stockpilot.entity.Order;
import com.stockpilot.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Lightweight projection used for order list views, where fetching every line item would be wasteful. */
public record OrderSummaryResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {
    // Line items are intentionally omitted here to avoid N+1 lazy loading on list views;
    // fetch a single order (GET /orders/{id}) to see its items.
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.getCreatedBy().getFullName(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
