package com.stockpilot.dto.inventory;

import com.stockpilot.entity.AdjustmentType;
import com.stockpilot.entity.InventoryAdjustment;

import java.time.Instant;

public record AdjustmentResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer quantityChange,
        AdjustmentType adjustmentType,
        String reason,
        Integer resultingQuantity,
        Long performedByUserId,
        String performedByName,
        Instant createdAt
) {
    public static AdjustmentResponse from(InventoryAdjustment a) {
        return new AdjustmentResponse(
                a.getId(), a.getProduct().getId(), a.getProduct().getName(), a.getProduct().getSku(),
                a.getQuantityChange(), a.getAdjustmentType(), a.getReason(), a.getResultingQuantity(),
                a.getUser().getId(), a.getUser().getFullName(), a.getCreatedAt());
    }
}
