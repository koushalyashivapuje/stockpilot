package com.stockpilot.controller;

import com.stockpilot.dto.auth.WorkspaceMemberResponse;
import com.stockpilot.security.CurrentUser;
import com.stockpilot.service.WorkspaceAccessService;
import com.stockpilot.repository.WorkspaceMembershipRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final CurrentUser currentUser;

    public WorkspaceController(WorkspaceMembershipRepository membershipRepository,
                                WorkspaceAccessService workspaceAccessService, CurrentUser currentUser) {
        this.membershipRepository = membershipRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> members(@PathVariable Long workspaceId) {
        // Any member can see the membership roster; only Owners can add members (AuthController).
        workspaceAccessService.requireMembership(workspaceId, currentUser.id());
        List<WorkspaceMemberResponse> members = membershipRepository.findByWorkspace_Id(workspaceId).stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
        return ResponseEntity.ok(members);
    }
}
