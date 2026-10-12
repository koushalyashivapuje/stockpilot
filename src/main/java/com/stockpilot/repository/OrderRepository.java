package com.stockpilot.repository;

import com.stockpilot.entity.Order;
import com.stockpilot.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id and o.workspace.id = :workspaceId")
    Optional<Order> findByIdAndWorkspace_Id(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    /**
     * Locks the order row and eagerly loads its items in the same query, so the returned
     * entity can be read (including items) after the lock is taken, inside the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o left join fetch o.items where o.id = :id and o.workspace.id = :workspaceId")
    Optional<Order> findByIdForUpdate(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    Page<Order> findByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);

    List<Order> findTop5ByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId);

    long countByWorkspace_IdAndStatus(Long workspaceId, OrderStatus status);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.workspace.id = :workspaceId " +
            "and o.status <> 'CANCELLED' and o.createdAt >= :from and o.createdAt <= :to")
    BigDecimal sumNonCancelledTotalInRange(@Param("workspaceId") Long workspaceId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);
}
