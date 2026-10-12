package com.stockpilot.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true, message = "Unit price must not be negative") BigDecimal unitPrice,
        @NotNull @Min(value = 0, message = "Stock quantity must not be negative") Integer stockQuantity,
        @NotNull @Min(value = 0, message = "Low stock threshold must not be negative") Integer lowStockThreshold,
        Boolean active
) {
}
