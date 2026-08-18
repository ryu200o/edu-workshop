package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ChangePasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutAllCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.UpdateProfileCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetMeQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Driving HTTP adapter for the self-service IAM APIs (plan §1.1, ADR 0020 §2): profile read/update,
 * password change, and per-device / global logout. Chain rule {@code authenticated()} — the caller id
 * always comes from the JWT {@link AuthenticatedPrincipal}, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/iam/me")
class IamSelfController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    IamSelfController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @GetMapping
    ResponseEntity<MeView> me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(queryBus.execute(new GetMeQuery(principal.userId())));
    }

    @PutMapping("/profile")
    ResponseEntity<MeView> updateProfile(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                         @RequestBody Map<String, Object> body) {
        if (body.containsKey("email") || body.containsKey("password")) {
            throw new IllegalArgumentException(
                    "email and password are not editable here; change the password via "
                            + "POST /api/v1/iam/me/change-password");
        }
        commandBus.execute(new UpdateProfileCommand(
                principal.userId(),
                asString(body.get("fullName")),
                asString(body.get("phoneNumber")),
                asString(body.get("studentCode")),
                asString(body.get("avatarUrl"))));
        return ResponseEntity.ok(queryBus.execute(new GetMeQuery(principal.userId())));
    }

    @PostMapping("/change-password")
    ResponseEntity<ChangePasswordCommand.Result> changePassword(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestBody ChangePasswordRequest request) {
        ChangePasswordCommand.Result result = commandBus.execute(new ChangePasswordCommand(
                principal.userId(), request.currentPassword(), request.newPassword()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout-all")
    ResponseEntity<LogoutAllCommand.Result> logoutAll(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        LogoutAllCommand.Result result = commandBus.execute(new LogoutAllCommand(principal.userId()));
        return ResponseEntity.ok(result);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}