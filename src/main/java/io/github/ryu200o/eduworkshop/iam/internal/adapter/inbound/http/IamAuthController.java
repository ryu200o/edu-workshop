package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenResponse;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenUseCase;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ForgotPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RegisterCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ResetPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.VerifyEmailCommand;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving HTTP adapter for the public IAM auth APIs (plan §2.1-2.4, ADR 0020 §2). PermitAll at the
 * security chain level. Login/refresh are Security Token Minting Operations handled by the
 * {@link AuthTokenUseCase} (ADR 0021) and return 200 with the session payload; all other endpoints
 * are strictly-void {@link CommandBus} commands returning 201/204 with no body. Error handling is
 * centralized in {@link IamExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/iam/auth")
class IamAuthController {

    private final CommandBus commandBus;
    private final AuthTokenUseCase authTokenUseCase;

    IamAuthController(CommandBus commandBus, AuthTokenUseCase authTokenUseCase) {
        this.commandBus = commandBus;
        this.authTokenUseCase = authTokenUseCase;
    }

    @Idempotent
    @PostMapping("/register")
    ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        UUID userId = UUID.randomUUID();
        commandBus.execute(new RegisterCommand(userId, request.email(), request.password(), request.fullName()));
        return ResponseEntity.created(URI.create("/api/v1/iam/users/" + userId)).build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@RequestBody TokenRequest request) {
        commandBus.execute(new VerifyEmailCommand(request.token()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authTokenUseCase.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthTokenResponse> refresh(@RequestBody TokenRequest request) {
        return ResponseEntity.ok(authTokenUseCase.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody TokenRequest request) {
        commandBus.execute(new LogoutCommand(request.refreshToken()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@RequestBody EmailRequest request) {
        commandBus.execute(new ForgotPasswordCommand(request.email()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        commandBus.execute(new ResetPasswordCommand(request.token(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    record RegisterRequest(String email, String password, String fullName) {
    }

    record LoginRequest(String email, String password) {
    }

    record TokenRequest(String token, String refreshToken) {
    }

    record EmailRequest(String email) {
    }

    record ResetPasswordRequest(String token, String newPassword) {
    }
}
