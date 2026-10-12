package com.stockpilot.service;

import com.stockpilot.dto.inventory.AdjustmentRequest;
import com.stockpilot.entity.*;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest extends IntegrationTestBase {

    @Autowired private InventoryService inventoryService;

    @Test
    void stockCannotGoNegative() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("staff@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-1", new BigDecimal("10.00"), 5, 1);

        assertThatThrownBy(() -> inventoryService.adjustStock(workspace.getId(), product.getId(), user.getId(),
                new AdjustmentRequest(-10, AdjustmentType.DAMAGED_GOODS, "too many damaged")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void validAdjustmentUpdatesStockAndRecordsAuditRow() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("staff2@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-2", new BigDecimal("10.00"), 5, 1);

        var adjustment = inventoryService.adjustStock(workspace.getId(), product.getId(), user.getId(),
                new AdjustmentRequest(20, AdjustmentType.RECEIVED_STOCK, "weekly delivery"));

        assertThat(adjustment.getResultingQuantity()).isEqualTo(25);
        assertThat(adjustment.getProduct().getStockQuantity()).isEqualTo(25);
    }

    @Test
    void zeroQuantityChangeIsRejected() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("staff3@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-3", new BigDecimal("10.00"), 5, 1);

        assertThatThrownBy(() -> inventoryService.adjustStock(workspace.getId(), product.getId(), user.getId(),
                new AdjustmentRequest(0, AdjustmentType.OTHER, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
