package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ForgotPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LoginCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RefreshCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RegisterCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ResetPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.VerifyEmailCommand;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving HTTP adapter for the 6 public IAM auth APIs (plan §2.1-2.4, ADR 0020 §2). PermitAll at the
 * security chain level; talks exclusively to the shared {@link CommandBus}. Error handling is
 * centralized in {@link IamExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/iam/auth")
class IamAuthController {

    private final CommandBus commandBus;

    IamAuthController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/register")
    ResponseEntity<RegisterCommand.Result> register(@RequestBody RegisterRequest request) {
        RegisterCommand.Result result = commandBus.execute(
                new RegisterCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/verify-email")
    ResponseEntity<VerifyEmailCommand.Result> verifyEmail(@RequestBody TokenRequest request) {
        VerifyEmailCommand.Result result = commandBus.execute(new VerifyEmailCommand(request.token()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    ResponseEntity<LoginCommand.Result> login(@RequestBody LoginRequest request) {
        LoginCommand.Result result = commandBus.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshCommand.Result> refresh(@RequestBody TokenRequest request) {
        RefreshCommand.Result result = commandBus.execute(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/forgot-password")
    ResponseEntity<ForgotPasswordCommand.Result> forgotPassword(@RequestBody EmailRequest request) {
        ForgotPasswordCommand.Result result = commandBus.execute(new ForgotPasswordCommand(request.email()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reset-password")
    ResponseEntity<ResetPasswordCommand.Result> resetPassword(@RequestBody ResetPasswordRequest request) {
        ResetPasswordCommand.Result result = commandBus.execute(
                new ResetPasswordCommand(request.token(), request.newPassword()));
        return ResponseEntity.ok(result);
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
