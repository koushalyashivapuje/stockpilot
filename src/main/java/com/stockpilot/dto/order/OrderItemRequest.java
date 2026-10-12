package com.stockpilot.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(value = 1, message = "Quantity must be a positive integer") Integer quantity
) {
}
