package com.stockpilot.security;

/** Minimal principal placed into the Spring Security context after a valid JWT is verified. */
public record AuthenticatedUser(Long userId, String email) {
}
