package com.stockpilot.service;

import com.stockpilot.entity.Role;
import com.stockpilot.entity.WorkspaceMembership;
import com.stockpilot.exception.ForbiddenException;
import com.stockpilot.repository.WorkspaceMembershipRepository;
import org.springframework.stereotype.Service;

/**
 * Central place where every workspace-scoped request is checked against the caller's
 * actual membership, re-read from the database on each call (never trusted from the JWT
 * or from the request). This is what "workspace isolation" and "server-side role
 * enforcement" come down to in this codebase.
 */
@Service
public class WorkspaceAccessService {

    private final WorkspaceMembershipRepository membershipRepository;

    public WorkspaceAccessService(WorkspaceMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /** Returns the caller's membership in the workspace, or throws 403 if they are not a member. */
    public WorkspaceMembership requireMembership(Long workspaceId, Long userId) {
        return membershipRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                .orElseThrow(() -> new ForbiddenException(
                        "You are not a member of this workspace."));
    }

    /** Like {@link #requireMembership} but additionally requires the OWNER role. */
    public WorkspaceMembership requireOwner(Long workspaceId, Long userId) {
        WorkspaceMembership membership = requireMembership(workspaceId, userId);
        if (membership.getRole() != Role.OWNER) {
            throw new ForbiddenException("This action requires the Owner role.");
        }
        return membership;
    }
}
