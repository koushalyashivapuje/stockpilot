package com.stockpilot.repository;

import com.stockpilot.entity.WorkspaceMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, Long> {

    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);

    List<WorkspaceMembership> findByUser_Id(Long userId);

    List<WorkspaceMembership> findByWorkspace_Id(Long workspaceId);

    boolean existsByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
}
