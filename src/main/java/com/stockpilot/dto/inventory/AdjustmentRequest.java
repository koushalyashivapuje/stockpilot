package com.stockpilot.dto.inventory;

import com.stockpilot.entity.AdjustmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustmentRequest(
        @NotNull Integer quantityChange,
        @NotNull AdjustmentType adjustmentType,
        @Size(max = 500) String reason
) {
}
