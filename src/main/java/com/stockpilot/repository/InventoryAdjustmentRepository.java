package com.stockpilot.repository;

import com.stockpilot.entity.InventoryAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, Long>, JpaSpecificationExecutor<InventoryAdjustment> {

    Page<InventoryAdjustment> findByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
}
