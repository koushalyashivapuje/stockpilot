package com.stockpilot.controller;

import com.stockpilot.dto.auth.*;
import com.stockpilot.security.CurrentUser;
import com.stockpilot.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Owner-only: add a staff member to a workspace. See {@link InviteStaffRequest}. */
    @PostMapping("/workspaces/{workspaceId}/staff")
    public ResponseEntity<Map<String, Object>> addStaff(@PathVariable Long workspaceId,
                                                          @Valid @RequestBody InviteStaffRequest request) {
        String temporaryPassword = authService.addStaffMember(workspaceId, currentUser.id(), request);
        if (temporaryPassword != null) {
            return ResponseEntity.ok(Map.of(
                    "message", "New staff account created.",
                    "email", request.email(),
                    "temporaryPassword", temporaryPassword
            ));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Existing user added to the workspace as staff.",
                "email", request.email()
        ));
    }
}
