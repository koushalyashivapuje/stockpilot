package com.stockpilot.dto.auth;

import java.util.List;

public record AuthResponse(
        String token,
        Long userId,
        String email,
        String fullName,
        List<WorkspaceMembershipDto> workspaces
) {
    public record WorkspaceMembershipDto(Long workspaceId, String workspaceName, String workspaceSlug, String role) {
    }
}
