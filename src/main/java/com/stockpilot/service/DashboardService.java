package com.stockpilot.service;

import com.stockpilot.dto.dashboard.DashboardResponse;
import com.stockpilot.dto.inventory.AdjustmentResponse;
import com.stockpilot.dto.order.OrderSummaryResponse;
import com.stockpilot.entity.OrderStatus;
import com.stockpilot.repository.InventoryAdjustmentRepository;
import com.stockpilot.repository.OrderRepository;
import com.stockpilot.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

@Service
public class DashboardService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public DashboardService(ProductRepository productRepository, OrderRepository orderRepository,
                             InventoryAdjustmentRepository adjustmentRepository,
                             WorkspaceAccessService workspaceAccessService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long workspaceId, Long actingUserId, Instant from, Instant to) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        Instant rangeTo = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.minus(30, ChronoUnit.DAYS);

        long activeProductCount = productRepository.countByWorkspace_IdAndActiveTrue(workspaceId);
        long lowStockCount = productRepository.countLowStock(workspaceId);

        Map<OrderStatus, Long> orderCounts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            orderCounts.put(status, orderRepository.countByWorkspace_IdAndStatus(workspaceId, status));
        }

        BigDecimal totalOrderValue = orderRepository.sumNonCancelledTotalInRange(workspaceId, rangeFrom, rangeTo);

        var recentOrders = orderRepository.findTop5ByWorkspace_IdOrderByCreatedAtDesc(workspaceId).stream()
                // mapped to a DTO while still inside this @Transactional method, so the lazy
                // createdBy association on each order is safe to read here.
                .map(OrderSummaryResponse::from)
                .toList();

        var recentAdjustments = adjustmentRepository
                .findByWorkspace_IdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(AdjustmentResponse::from)
                .toList();

        return new DashboardResponse(activeProductCount, lowStockCount, orderCounts, totalOrderValue,
                rangeFrom, rangeTo, recentOrders, recentAdjustments);
    }
}
