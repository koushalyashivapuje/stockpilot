package com.stockpilot.service;

import com.stockpilot.dto.order.OrderCreateRequest;
import com.stockpilot.dto.order.OrderItemRequest;
import com.stockpilot.entity.*;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "two concurrent requests cannot jointly consume more stock than exists" invariant.
 * The product starts with 3 units in stock; 5 threads race to each order 1 unit at the same
 * instant. Exactly 3 should succeed (the row lock in ProductService#getForStockMutation /
 * ProductRepository#findByIdForUpdate serializes the competing transactions), the rest must
 * fail cleanly on insufficient stock, and stock must never go negative or below zero.
 */
class OrderConcurrencyTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;

    @Test
    void concurrentOrdersCannotOversellLimitedStock() throws Exception {
        Workspace workspace = testData.createWorkspace("Acme");
        User user = testData.createUser("race@acme.test");
        testData.addMember(workspace, user, Role.STAFF);
        Product product = testData.createProduct(workspace, "SKU-RACE", new BigDecimal("5.00"), 3, 1);

        int attackers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(attackers);
        CountDownLatch readyLatch = new CountDownLatch(attackers);
        CountDownLatch goLatch = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < attackers; i++) {
            futures.add(pool.submit(() -> {
                readyLatch.countDown();
                try {
                    goLatch.await();
                    orderService.create(workspace.getId(), user.getId(),
                            new OrderCreateRequest(List.of(new OrderItemRequest(product.getId(), 1))));
                    succeeded.incrementAndGet();
                } catch (Exception ex) {
                    failed.incrementAndGet();
                } finally {
                    return null;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        goLatch.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(3);
        assertThat(failed.get()).isEqualTo(2);

        Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isZero();
    }
}
