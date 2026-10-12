package com.stockpilot.dto.auth;

import com.stockpilot.entity.WorkspaceMembership;

public record WorkspaceMemberResponse(Long userId, String email, String fullName, String role) {
    public static WorkspaceMemberResponse from(WorkspaceMembership m) {
        return new WorkspaceMemberResponse(m.getUser().getId(), m.getUser().getEmail(),
                m.getUser().getFullName(), m.getRole().name());
    }
}
