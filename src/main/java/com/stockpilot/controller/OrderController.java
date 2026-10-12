package com.stockpilot.controller;

import com.stockpilot.dto.common.PageResponse;
import com.stockpilot.dto.order.OrderCreateRequest;
import com.stockpilot.dto.order.OrderResponse;
import com.stockpilot.dto.order.OrderSummaryResponse;
import com.stockpilot.entity.Order;
import com.stockpilot.entity.OrderStatus;
import com.stockpilot.security.CurrentUser;
import com.stockpilot.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/orders")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@PathVariable Long workspaceId,
                                                  @Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.create(workspaceId, currentUser.id(), request);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(@PathVariable Long workspaceId, @PathVariable Long orderId) {
        Order order = orderService.getForViewing(workspaceId, orderId, currentUser.id());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable Long workspaceId, @PathVariable Long orderId) {
        Order order = orderService.confirm(workspaceId, orderId, currentUser.id());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/complete")
    public ResponseEntity<OrderResponse> complete(@PathVariable Long workspaceId, @PathVariable Long orderId) {
        Order order = orderService.complete(workspaceId, orderId, currentUser.id());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long workspaceId, @PathVariable Long orderId) {
        Order order = orderService.cancel(workspaceId, orderId, currentUser.id());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<Order> result = orderService.search(workspaceId, currentUser.id(), status, orderId, from, to,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PageResponse.of(result.map(OrderSummaryResponse::from)));
    }
}
