package com.stockpilot.service;

import com.stockpilot.dto.order.OrderCreateRequest;
import com.stockpilot.dto.order.OrderItemRequest;
import com.stockpilot.entity.*;
import com.stockpilot.exception.ForbiddenException;
import com.stockpilot.exception.ResourceNotFoundException;
import com.stockpilot.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceIsolationTest extends IntegrationTestBase {

    @Autowired private ProductService productService;
    @Autowired private OrderService orderService;

    @Test
    void memberOfOneWorkspaceCannotReadAnotherWorkspacesProductById() {
        Workspace workspaceA = testData.createWorkspace("Acme");
        Workspace workspaceB = testData.createWorkspace("Globex");
        User userA = testData.createUser("a@acme.test");
        User userB = testData.createUser("b@globex.test");
        testData.addMember(workspaceA, userA, Role.OWNER);
        testData.addMember(workspaceB, userB, Role.OWNER);

        Product productInA = testData.createProduct(workspaceA, "SKU-ISO-1", new BigDecimal("5.00"), 10, 1);

        // userB is a real member of workspaceB, but tries to reach workspaceA's product by
        // guessing its id under workspaceB's own URL - must not be found, not leaked.
        assertThatThrownBy(() -> productService.getForViewing(workspaceB.getId(), productInA.getId(), userB.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonMemberCannotAccessWorkspaceAtAll() {
        Workspace workspaceA = testData.createWorkspace("Acme");
        User outsider = testData.createUser("outsider@nowhere.test");
        Product product = testData.createProduct(workspaceA, "SKU-ISO-2", new BigDecimal("5.00"), 10, 1);

        assertThatThrownBy(() -> productService.getForViewing(workspaceA.getId(), product.getId(), outsider.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotCreateAnOrderAgainstAnotherWorkspacesProduct() {
        Workspace workspaceA = testData.createWorkspace("Acme");
        Workspace workspaceB = testData.createWorkspace("Globex");
        User userB = testData.createUser("b2@globex.test");
        testData.addMember(workspaceB, userB, Role.STAFF);

        Product productInA = testData.createProduct(workspaceA, "SKU-ISO-3", new BigDecimal("5.00"), 10, 1);

        // userB is a legitimate member of workspaceB and tries to order a product that only
        // exists in workspace A, through workspace B's order endpoint.
        assertThatThrownBy(() -> orderService.create(workspaceB.getId(), userB.getId(),
                new OrderCreateRequest(List.of(new OrderItemRequest(productInA.getId(), 1)))))
                .isInstanceOf(com.stockpilot.exception.BusinessRuleException.class);
    }
}
