package com.stockpilot.service;

import com.stockpilot.dto.product.ProductRequest;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.Workspace;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.ResourceNotFoundException;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.repository.WorkspaceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public ProductService(ProductRepository productRepository, WorkspaceRepository workspaceRepository,
                           WorkspaceAccessService workspaceAccessService) {
        this.productRepository = productRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional
    public Product create(Long workspaceId, Long actingUserId, ProductRequest request) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        if (productRepository.existsByWorkspace_IdAndSkuIgnoreCase(workspaceId, request.sku())) {
            throw new ConflictException("A product with SKU '" + request.sku() + "' already exists in this workspace.");
        }

        Workspace workspace = workspaceRepository.getReferenceById(workspaceId);

        Product product = Product.builder()
                .workspace(workspace)
                .name(request.name())
                .sku(request.sku())
                .description(request.description())
                .unitPrice(request.unitPrice())
                .stockQuantity(request.stockQuantity())
                .lowStockThreshold(request.lowStockThreshold())
                .active(request.active() == null || request.active())
                .build();

        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long workspaceId, Long productId, Long actingUserId, ProductRequest request) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        Product product = getOwnedProduct(workspaceId, productId);

        if (productRepository.existsByWorkspace_IdAndSkuIgnoreCaseAndIdNot(workspaceId, request.sku(), productId)) {
            throw new ConflictException("A product with SKU '" + request.sku() + "' already exists in this workspace.");
        }

        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setUnitPrice(request.unitPrice());
        product.setLowStockThreshold(request.lowStockThreshold());
        if (request.active() != null) {
            product.setActive(request.active());
        }
        // Stock quantity is intentionally NOT editable here; it only changes through
        // audited inventory adjustments or order lifecycle effects (see InventoryService / OrderService).
        return product;
    }

    @Transactional
    public void deactivate(Long workspaceId, Long productId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        Product product = getOwnedProduct(workspaceId, productId);
        product.setActive(false);
    }

    /** Membership-checked lookup for a single product, for use by controllers. */
    @Transactional(readOnly = true)
    public Product getForViewing(Long workspaceId, Long productId, Long actingUserId) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);
        return getOwnedProduct(workspaceId, productId);
    }

    /** Internal lookup without a membership check; callers must have already verified access. */
    @Transactional(readOnly = true)
    public Product getOwnedProduct(Long workspaceId, Long productId) {
        return productRepository.findByIdAndWorkspace_Id(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(Long workspaceId, Long actingUserId, String query, Boolean active,
                                 Boolean lowStockOnly, Pageable pageable) {
        workspaceAccessService.requireMembership(workspaceId, actingUserId);

        Specification<Product> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspace").get("id"), workspaceId));

            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like)
                ));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (Boolean.TRUE.equals(lowStockOnly)) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("lowStockThreshold")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return productRepository.findAll(spec, pageable);
    }

    /** Used by OrderService to validate an order line and load the product under a row lock. */
    @Transactional
    public Product getForStockMutation(Long workspaceId, Long productId) {
        return productRepository.findByIdForUpdate(productId, workspaceId)
                .orElseThrow(() -> new BusinessRuleException("Product " + productId + " was not found in this workspace."));
    }
}
