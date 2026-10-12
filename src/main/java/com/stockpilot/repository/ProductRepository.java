package com.stockpilot.repository;

import com.stockpilot.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndWorkspace_Id(Long id, Long workspaceId);

    boolean existsByWorkspace_IdAndSkuIgnoreCase(Long workspaceId, String sku);

    boolean existsByWorkspace_IdAndSkuIgnoreCaseAndIdNot(Long workspaceId, String sku, Long id);

    /** Row lock used during stock-mutating operations to prevent lost updates under concurrency. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.workspace.id = :workspaceId")
    Optional<Product> findByIdForUpdate(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    long countByWorkspace_IdAndActiveTrue(Long workspaceId);

    @Query("select count(p) from Product p where p.workspace.id = :workspaceId and p.active = true and p.stockQuantity <= p.lowStockThreshold")
    long countLowStock(@Param("workspaceId") Long workspaceId);

    Page<Product> findByWorkspace_Id(Long workspaceId, Pageable pageable);
}
