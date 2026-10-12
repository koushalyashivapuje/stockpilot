package com.stockpilot.service;

import com.stockpilot.dto.order.OrderCreateRequest;
import com.stockpilot.dto.order.OrderItemRequest;
import com.stockpilot.dto.product.ProductRequest;
import com.stockpilot.entity.*;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;

    @Test
    void backendCalculatesLineAndOrderTotals() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u1@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product p1 = testData.createProduct(workspace, "SKU-1", new BigDecimal("12.50"), 10, 1);
        Product p2 = testData.createProduct(workspace, "SKU-2", new BigDecimal("3.00"), 10, 1);

        var order = orderService.create(workspace.getId(), user.getId(), new OrderCreateRequest(List.of(
                new OrderItemRequest(p1.getId(), 2),
                new OrderItemRequest(p2.getId(), 5)
        )));

        // 2 * 12.50 + 5 * 3.00 = 25.00 + 15.00 = 40.00
        assertThat(order.getTotalAmount()).isEqualByComparingTo("40.00");
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void orderIsRejectedWhenStockIsInsufficient() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u2@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-3", new BigDecimal("5.00"), 3, 1);

        assertThatThrownBy(() -> orderService.create(workspace.getId(), user.getId(),
                new OrderCreateRequest(List.of(new OrderItemRequest(product.getId(), 10)))))
                .isInstanceOf(BusinessRuleException.class);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(3); // untouched
    }

    @Test
    void multiItemOrderIsAtomicWhenOneLineFails() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u3@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product plentiful = testData.createProduct(workspace, "SKU-4", new BigDecimal("5.00"), 100, 1);
        Product scarce = testData.createProduct(workspace, "SKU-5", new BigDecimal("5.00"), 2, 1);

        assertThatThrownBy(() -> orderService.create(workspace.getId(), user.getId(), new OrderCreateRequest(List.of(
                new OrderItemRequest(plentiful.getId(), 10),
                new OrderItemRequest(scarce.getId(), 50) // fails
        )))).isInstanceOf(BusinessRuleException.class);

        // Neither product's stock was touched - no partial deduction.
        assertThat(productRepository.findById(plentiful.getId()).orElseThrow().getStockQuantity()).isEqualTo(100);
        assertThat(productRepository.findById(scarce.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void cancellationRestoresStockExactlyOnceEvenIfRepeated() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u4@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-6", new BigDecimal("5.00"), 10, 1);

        var order = orderService.create(workspace.getId(), user.getId(),
                new OrderCreateRequest(List.of(new OrderItemRequest(product.getId(), 4))));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(6);

        orderService.cancel(workspace.getId(), order.getId(), user.getId());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);

        // Cancel again (simulating a retried request) - must be a no-op, not a second restoration.
        orderService.cancel(workspace.getId(), order.getId(), user.getId());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void invalidStatusTransitionsAreRejected() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u5@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-7", new BigDecimal("5.00"), 10, 1);

        var order = orderService.create(workspace.getId(), user.getId(),
                new OrderCreateRequest(List.of(new OrderItemRequest(product.getId(), 1))));

        // Cannot complete a Pending order directly - must be confirmed first.
        assertThatThrownBy(() -> orderService.complete(workspace.getId(), order.getId(), user.getId()))
                .isInstanceOf(ConflictException.class);

        orderService.confirm(workspace.getId(), order.getId(), user.getId());
        orderService.complete(workspace.getId(), order.getId(), user.getId());

        // A Completed order can never be cancelled.
        assertThatThrownBy(() -> orderService.cancel(workspace.getId(), order.getId(), user.getId()))
                .isInstanceOf(ConflictException.class);

        // And cannot be confirmed again either.
        assertThatThrownBy(() -> orderService.confirm(workspace.getId(), order.getId(), user.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void changingProductPriceDoesNotAlterExistingOrderHistory() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u6@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-8", new BigDecimal("20.00"), 10, 1);

        var order = orderService.create(workspace.getId(), user.getId(),
                new OrderCreateRequest(List.of(new OrderItemRequest(product.getId(), 1))));
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");

        // Price changes after the order was placed.
        productService.update(workspace.getId(), product.getId(), user.getId(),
                new ProductRequest("Product SKU-8", "SKU-8", null, new BigDecimal("99.00"), 10, 1, true));

        var reloadedOrder = orderService.getForViewing(workspace.getId(), order.getId(), user.getId());
        assertThat(reloadedOrder.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(reloadedOrder.getItems().get(0).getUnitPriceSnapshot()).isEqualByComparingTo("20.00");
    }

    @Test
    void duplicateLineItemsForSameProductAreCombinedConsistently() {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("u7@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-9", new BigDecimal("2.00"), 10, 1);

        var order = orderService.create(workspace.getId(), user.getId(), new OrderCreateRequest(List.of(
                new OrderItemRequest(product.getId(), 2),
                new OrderItemRequest(product.getId(), 3)
        )));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("10.00");
    }
}
