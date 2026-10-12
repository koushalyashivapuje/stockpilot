package com.stockpilot.dto.product;

import com.stockpilot.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal unitPrice,
        Integer stockQuantity,
        Integer lowStockThreshold,
        boolean active,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getDescription(),
                p.getUnitPrice(), p.getStockQuantity(), p.getLowStockThreshold(), p.isActive(),
                p.isLowStock(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
