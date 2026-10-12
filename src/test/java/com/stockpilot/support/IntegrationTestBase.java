package com.stockpilot.support;

import com.stockpilot.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected TestDataFactory testData;

    @Autowired private OrderRepository orderRepository;
    @Autowired private InventoryAdjustmentRepository adjustmentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WorkspaceMembershipRepository membershipRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private UserRepository userRepository;

    // Deliberately NOT @Transactional at the class level: several tests (concurrency,
    // multi-step lifecycle calls) need each service call to commit its own transaction,
    // the same way real HTTP requests would. Data is cleaned up after each test instead.
    @AfterEach
    @Transactional
    void cleanDatabase() {
        orderRepository.deleteAll();
        adjustmentRepository.deleteAll();
        productRepository.deleteAll();
        membershipRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
    }
}
