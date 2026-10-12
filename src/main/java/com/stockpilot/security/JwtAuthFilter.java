package com.stockpilot.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts and verifies the bearer JWT on every request, then places an
 * {@link AuthenticatedUser} on the security context. The user id here is the
 * only source of "who is making this request" used anywhere downstream -
 * nothing from the request body or path is ever trusted for identity.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                Long userId = Long.valueOf(claims.getSubject());
                String email = claims.get("email", String.class);
                AuthenticatedUser principal = new AuthenticatedUser(userId, email);

                var authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired token: leave context unauthenticated; downstream entry point returns 401.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
