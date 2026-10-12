package com.stockpilot.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be at least 8 characters") String password,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 255) String workspaceName
) {
}
