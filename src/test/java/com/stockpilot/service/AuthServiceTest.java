package com.stockpilot.service;

import com.stockpilot.dto.auth.LoginRequest;
import com.stockpilot.dto.auth.RegisterRequest;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.UnauthorizedException;
import com.stockpilot.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest extends IntegrationTestBase {

    @Autowired private AuthService authService;

    @Test
    void registeringCreatesAWorkspaceWithTheUserAsOwner() {
        var response = authService.register(
                new RegisterRequest("owner@newco.test", "Password123!", "New Owner", "NewCo"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.workspaces()).hasSize(1);
        assertThat(response.workspaces().get(0).role()).isEqualTo("OWNER");
    }

    @Test
    void duplicateEmailRegistrationIsRejected() {
        authService.register(new RegisterRequest("dup@newco.test", "Password123!", "First", "FirstCo"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dup@newco.test", "Password123!", "Second", "SecondCo")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        authService.register(new RegisterRequest("login@newco.test", "Password123!", "Someone", "SomeCo"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("login@newco.test", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginWithCorrectCredentialsReturnsTokenAndWorkspaces() {
        authService.register(new RegisterRequest("login2@newco.test", "Password123!", "Someone", "SomeCo2"));

        var response = authService.login(new LoginRequest("login2@newco.test", "Password123!"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.workspaces()).hasSize(1);
    }
}
