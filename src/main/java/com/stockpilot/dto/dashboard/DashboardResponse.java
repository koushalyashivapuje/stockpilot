package com.stockpilot.dto.dashboard;

import com.stockpilot.dto.inventory.AdjustmentResponse;
import com.stockpilot.dto.order.OrderSummaryResponse;
import com.stockpilot.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Metric definitions (also documented in the README):
 * - activeProductCount: products in this workspace with active = true.
 * - lowStockCount: active products where stockQuantity <= lowStockThreshold.
 * - orderCountsByStatus: count of orders in this workspace, grouped by status (all statuses, unfiltered by date).
 * - totalOrderValue: sum of totalAmount for orders in [rangeFrom, rangeTo] with status <> CANCELLED
 *   (Pending + Confirmed + Completed are all included, since stock/value is already committed at creation
 *   under the reserve-at-creation strategy; only Cancelled orders are excluded).
 * - recentOrders / recentAdjustments: the 5 most recent of each, most recent first.
 */
public record DashboardResponse(
        long activeProductCount,
        long lowStockCount,
        Map<OrderStatus, Long> orderCountsByStatus,
        BigDecimal totalOrderValue,
        Instant rangeFrom,
        Instant rangeTo,
        List<OrderSummaryResponse> recentOrders,
        List<AdjustmentResponse> recentAdjustments
) {
}
