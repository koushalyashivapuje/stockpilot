package com.stockpilot.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Demo-simplified staff provisioning (no outbound email). The Owner supplies the new staff
 * member's email and name; if no account exists yet one is created with a generated temporary
 * password returned once in the response, which the Owner is expected to share out-of-band.
 * This is documented as a known simplification in the README.
 */
public record InviteStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName
) {
}
