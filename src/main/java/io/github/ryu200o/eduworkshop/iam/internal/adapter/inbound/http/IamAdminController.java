package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminCreateUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminDisableUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminEnableUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminLockUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminResetPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminUnlockUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminUpdateRolesCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.GetUserDetailQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.ListUsersQuery;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Driving HTTP adapter for the admin IAM surface (plan §1.1, ADR 0020 §2): user creation
 * (OQ-4 ACTIVE + mcp), directory/detail reads, role management, lock/unlock, disable/enable, and
 * password reset. Chain rule {@code hasRole("ADMIN")} — this whole surface requires the {@code ADMIN}
 * global role.
 */
@RestController
@RequestMapping("/api/v1/iam/admin/users")
class IamAdminController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    IamAdminController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping
    ResponseEntity<AdminCreateUserCommand.Result> createUser(@RequestBody CreateUserRequest request) {
        AdminCreateUserCommand.Result result = commandBus.execute(new AdminCreateUserCommand(
                request.email(), request.fullName(), request.temporaryPassword(), request.roles()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    ResponseEntity<List<UserSummaryView>> listUsers() {
        return ResponseEntity.ok(queryBus.execute(new ListUsersQuery()));
    }

    @GetMapping("/{userId}")
    ResponseEntity<UserDetailView> getUserDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(queryBus.execute(new GetUserDetailQuery(userId)));
    }

    @PutMapping("/{userId}/roles")
    ResponseEntity<AdminUpdateRolesCommand.Result> updateRoles(@PathVariable UUID userId,
                                                              @RequestBody RolesRequest request) {
        AdminUpdateRolesCommand.Result result = commandBus.execute(
                new AdminUpdateRolesCommand(userId, request.roles()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{userId}/lock")
    ResponseEntity<AdminLockUserCommand.Result> lockUser(@PathVariable UUID userId) {
        AdminLockUserCommand.Result result = commandBus.execute(new AdminLockUserCommand(userId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{userId}/unlock")
    ResponseEntity<AdminUnlockUserCommand.Result> unlockUser(@PathVariable UUID userId) {
        AdminUnlockUserCommand.Result result = commandBus.execute(new AdminUnlockUserCommand(userId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{userId}/disable")
    ResponseEntity<AdminDisableUserCommand.Result> disableUser(@PathVariable UUID userId) {
        AdminDisableUserCommand.Result result = commandBus.execute(new AdminDisableUserCommand(userId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{userId}/enable")
    ResponseEntity<AdminEnableUserCommand.Result> enableUser(@PathVariable UUID userId) {
        AdminEnableUserCommand.Result result = commandBus.execute(new AdminEnableUserCommand(userId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{userId}/reset-password")
    ResponseEntity<AdminResetPasswordCommand.Result> resetPassword(@PathVariable UUID userId,
                                                                  @RequestBody ResetPasswordRequest request) {
        AdminResetPasswordCommand.Result result = commandBus.execute(
                new AdminResetPasswordCommand(userId, request.newPassword()));
        return ResponseEntity.ok(result);
    }

    record CreateUserRequest(String email, String fullName, String temporaryPassword, Set<String> roles) {
    }

    record RolesRequest(Set<String> roles) {
    }

    record ResetPasswordRequest(String newPassword) {
    }
}