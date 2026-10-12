package com.stockpilot.service;

import com.stockpilot.dto.order.OrderCreateRequest;
import com.stockpilot.dto.order.OrderItemRequest;
import com.stockpilot.entity.*;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.ResourceNotFoundException;
import com.stockpilot.repository.OrderRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.repository.WorkspaceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Order lifecycle and stock strategy
 * ===================================
 * Strategy: RESERVE STOCK AT CREATION (assessment option 1).
 *   - Creating a Pending order immediately deducts stock for every line item.
 *   - Confirming a Pending order (PENDING -> CONFIRMED) does NOT touch stock again.
 *   - Completing a Confirmed order (CONFIRMED -> COMPLETED) does NOT touch stock again;
 *     the deduction made at creation is retained as the final, permanent effect.
 *   - Cancelling a Pending or Confirmed order restores every line item's quantity back
 *     to the product's stock, exactly once, guarded by the order's stockRestored flag.
 *
 * Atomicity: every item in the request is validated (exists in this workspace, active,
 * sufficient stock) BEFORE any stock is mutated. Only after every line passes validation
 * are the row locks' product quantities actually decremented and the order persisted - so
 * a failure on a later line never leaves an earlier line's stock partially deducted. The
 * whole method also runs inside one @Transactional boundary as a second line of defense.
 *
 * Concurrency: each product row involved is loaded with SELECT ... FOR UPDATE (via
 * ProductRepository#findByIdForUpdate), and rows are locked in ascending product-id order
 * across all call sites (create/cancel) to avoid lock-ordering deadlocks between concurrent
 * multi-item orders. Two concurrent requests against the same product therefore serialize at
 * the row lock, so neither can oversell stock the other has already reserved.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public OrderService(OrderRepository orderRepository, ProductService productService,
                         UserRepository userRepository, WorkspaceRepository workspaceRepository,
                         WorkspaceAccessService workspaceAccessService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional
    public Order create(Long workspaceId, Long actingUserId, OrderCreateRequest request) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        // Combine duplicate product lines consistently instead of rejecting them outright.
        LinkedHashMap<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantityByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // Lock rows in a deterministic order (ascending product id) to avoid deadlocking
        // against another concurrent order that references the same products.
        List<Long> orderedProductIds = new ArrayList<>(quantityByProduct.keySet());
        Collections.sort(orderedProductIds);

        Map<Long, Product> lockedProducts = new LinkedHashMap<>();
        for (Long productId : orderedProductIds) {
            Product product = productService.getForStockMutation(workspaceId, productId);
            lockedProducts.put(productId, product);
        }

        // --- Validation pass: nothing is mutated here. ---
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = lockedProducts.get(entry.getKey());
            Integer requestedQty = entry.getValue();

            if (!product.isActive()) {
                throw new BusinessRuleException("Product '" + product.getName() + "' is inactive and cannot be ordered.");
            }
            if (product.getStockQuantity() < requestedQty) {
                throw new BusinessRuleException("Insufficient stock for '" + product.getName() + "': requested "
                        + requestedQty + ", available " + product.getStockQuantity() + ".");
            }
        }

        // --- Mutation pass: only reached once every line item above passed validation. ---
        User creator = userRepository.getReferenceById(actingUserId);
        Workspace workspace = workspaceRepository.getReferenceById(workspaceId);

        Order order = Order.builder()
                .workspace(workspace)
                .createdBy(creator)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = lockedProducts.get(entry.getKey());
            int qty = entry.getValue();

            product.setStockQuantity(product.getStockQuantity() - qty);

            BigDecimal lineTotal = product.getUnitPrice().multiply(BigDecimal.valueOf(qty));
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .skuSnapshot(product.getSku())
                    .unitPriceSnapshot(product.getUnitPrice())
                    .quantity(qty)
                    .lineTotal(lineTotal)
                    .build();
            order.addItem(orderItem);
            total = total.add(lineTotal);
        }
        order.setTotalAmount(total);

        return orderRepository.save(order);
    }

    @Transactional
    public Order confirm(Long workspaceId, Long orderId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        Order order = getLockedOrder(workspaceId, orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Only a Pending order can be confirmed (current status: "
                    + order.getStatus() + ").");
        }
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }

    @Transactional
    public Order complete(Long workspaceId, Long orderId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        Order order = getLockedOrder(workspaceId, orderId);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ConflictException("Only a Confirmed order can be completed (current status: "
                    + order.getStatus() + ").");
        }
        order.setStatus(OrderStatus.COMPLETED);
        // Stock was already deducted at creation time under the reserve-at-creation strategy;
        // completion has no further stock effect.
        return order;
    }

    @Transactional
    public Order cancel(Long workspaceId, Long orderId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        Order order = getLockedOrder(workspaceId, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new ConflictException("A Completed order cannot be cancelled.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Idempotent: repeated/retried cancellation of an already-cancelled order is a no-op,
            // not an error, and never restores stock a second time.
            return order;
        }

        // order.getItems() lazily loads order_items, which is fine inside this transaction.
        List<OrderItem> items = new ArrayList<>(order.getItems());
        items.sort(Comparator.comparing(i -> i.getProduct().getId()));

        if (!order.isStockRestored()) {
            for (OrderItem item : items) {
                // Re-lock the product row (ascending product id across items) before restoring,
                // so a concurrent adjustment/order on the same product can't race this restore.
                Product product = productService.getForStockMutation(workspaceId, item.getProduct().getId());
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
            order.setStockRestored(true);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getForViewing(Long workspaceId, Long orderId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        return orderRepository.findByIdAndWorkspace_Id(orderId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    @Transactional(readOnly = true)
    public Page<Order> search(Long workspaceId, Long actingUserId, OrderStatus status, String orderId,
                               java.time.Instant from, java.time.Instant to, Pageable pageable) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        Specification<Order> spec = (root, cq, cb) -> {
            if (!Long.class.equals(cq.getResultType())) {
                // Avoid a lazy-load of createdBy when the response is rendered outside the
                // transaction; skipped for the separate COUNT query Spring Data issues for paging.
                root.fetch("createdBy", jakarta.persistence.criteria.JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspace").get("id"), workspaceId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (orderId != null && !orderId.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("id"), Long.parseLong(orderId.trim())));
                } catch (NumberFormatException ignored) {
                    predicates.add(cb.equal(root.get("id"), -1L)); // no match
                }
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable);
    }

    private Order getLockedOrder(Long workspaceId, Long orderId) {
        return orderRepository.findByIdForUpdate(orderId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }
}
