package com.stockpilot.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderCreateRequest(
        @NotEmpty(message = "An order must contain at least one line item") @Valid List<OrderItemRequest> items
) {
}
