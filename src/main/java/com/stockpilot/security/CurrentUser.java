package com.stockpilot.security;

import com.stockpilot.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Long id() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return user.userId();
    }
}
