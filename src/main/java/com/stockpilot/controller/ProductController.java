package com.stockpilot.controller;

import com.stockpilot.dto.common.PageResponse;
import com.stockpilot.dto.product.ProductRequest;
import com.stockpilot.dto.product.ProductResponse;
import com.stockpilot.entity.Product;
import com.stockpilot.security.CurrentUser;
import com.stockpilot.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;
    private final CurrentUser currentUser;

    public ProductController(ProductService productService, CurrentUser currentUser) {
        this.productService = productService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@PathVariable Long workspaceId,
                                                    @Valid @RequestBody ProductRequest request) {
        Product product = productService.create(workspaceId, currentUser.id(), request);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long workspaceId, @PathVariable Long productId,
                                                    @Valid @RequestBody ProductRequest request) {
        Product product = productService.update(workspaceId, productId, currentUser.id(), request);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deactivate(@PathVariable Long workspaceId, @PathVariable Long productId) {
        productService.deactivate(workspaceId, productId, currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> get(@PathVariable Long workspaceId, @PathVariable Long productId) {
        Product product = productService.getForViewing(workspaceId, productId, currentUser.id());
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "lowStock") Boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, allowedSortField(sortBy));

        Page<Product> result = productService.search(workspaceId, currentUser.id(), q, active, lowStockOnly,
                PageRequest.of(safePage, safeSize, sort));

        return ResponseEntity.ok(PageResponse.of(result.map(ProductResponse::from)));
    }

    private String allowedSortField(String requested) {
        return switch (requested) {
            case "name", "sku", "unitPrice", "stockQuantity", "createdAt", "updatedAt" -> requested;
            default -> "name";
        };
    }
}
