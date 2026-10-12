package com.stockpilot.service;

import com.stockpilot.dto.inventory.AdjustmentRequest;
import com.stockpilot.entity.InventoryAdjustment;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.User;
import com.stockpilot.entity.Workspace;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.repository.InventoryAdjustmentRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.repository.WorkspaceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock adjustments are the one place (outside the order lifecycle) where a product's
 * stockQuantity changes. Every change goes through here so it is always paired, in the
 * same transaction, with an immutable audit row - current stock and its history can
 * never drift apart.
 */
@Service
public class InventoryService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public InventoryService(InventoryAdjustmentRepository adjustmentRepository, ProductService productService,
                             UserRepository userRepository, WorkspaceRepository workspaceRepository,
                             WorkspaceAccessService workspaceAccessService) {
        this.adjustmentRepository = adjustmentRepository;
        this.productService = productService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional
    public InventoryAdjustment adjustStock(Long workspaceId, Long productId, Long actingUserId, AdjustmentRequest request) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        if (request.quantityChange() == null || request.quantityChange() == 0) {
            throw new BusinessRuleException("Quantity change must be a non-zero integer.");
        }

        // Row-locked read: two concurrent adjustments on the same product serialize here,
        // so the "stock can never go negative" check below cannot race.
        Product product = productService.getForStockMutation(workspaceId, productId);

        int newQuantity = product.getStockQuantity() + request.quantityChange();
        if (newQuantity < 0) {
            throw new BusinessRuleException("This adjustment would make stock negative (current: "
                    + product.getStockQuantity() + ", change: " + request.quantityChange() + ").");
        }

        product.setStockQuantity(newQuantity);

        User user = userRepository.getReferenceById(actingUserId);
        Workspace workspace = workspaceRepository.getReferenceById(workspaceId);

        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .workspace(workspace)
                .product(product)
                .user(user)
                .quantityChange(request.quantityChange())
                .adjustmentType(request.adjustmentType())
                .reason(request.reason())
                .resultingQuantity(newQuantity)
                .build();

        return adjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public Page<InventoryAdjustment> history(Long workspaceId, Long actingUserId, Long productId,
                                              Instant from, Instant to, Pageable pageable) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        Specification<InventoryAdjustment> spec = (root, cq, cb) -> {
            if (!Long.class.equals(cq.getResultType())) {
                root.fetch("product", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("user", jakarta.persistence.criteria.JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspace").get("id"), workspaceId));
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return adjustmentRepository.findAll(spec, pageable);
    }
}
