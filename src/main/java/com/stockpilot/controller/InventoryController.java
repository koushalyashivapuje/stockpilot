package com.stockpilot.controller;

import com.stockpilot.dto.common.PageResponse;
import com.stockpilot.dto.inventory.AdjustmentRequest;
import com.stockpilot.dto.inventory.AdjustmentResponse;
import com.stockpilot.entity.InventoryAdjustment;
import com.stockpilot.security.CurrentUser;
import com.stockpilot.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class InventoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryService inventoryService;
    private final CurrentUser currentUser;

    public InventoryController(InventoryService inventoryService, CurrentUser currentUser) {
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
    }

    @PostMapping("/products/{productId}/adjustments")
    public ResponseEntity<AdjustmentResponse> adjust(@PathVariable Long workspaceId, @PathVariable Long productId,
                                                        @Valid @RequestBody AdjustmentRequest request) {
        InventoryAdjustment adjustment = inventoryService.adjustStock(workspaceId, productId, currentUser.id(), request);
        return ResponseEntity.ok(AdjustmentResponse.from(adjustment));
    }

    @GetMapping("/inventory-adjustments")
    public ResponseEntity<PageResponse<AdjustmentResponse>> history(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<InventoryAdjustment> result = inventoryService.history(workspaceId, currentUser.id(), productId, from, to,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PageResponse.of(result.map(AdjustmentResponse::from)));
    }
}
