package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.CancelRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.RegisterWorkshopCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.VerifyRegistrationCommand;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanVerifyRegistrations;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Registration WRITE side (Command). Accepts only state-changing HTTP
 * methods and talks exclusively to the shared {@link CommandBus}. The acting user is read from the
 * {@link AuthenticatedPrincipal} (IAM Slice 5 — the {@code X-User-Id}/{@code X-Actor-Role} headers
 * are gone). Only a {@code VERIFIER} global role passes the verify gate (OQ-3C-1); the handler
 * enforces it from the role derived here. Error handling is centralized in
 * {@link RegistrationExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/registrations")
class RegistrationCommandController {

    private final CommandBus commandBus;
    private final RegistrationQrResolver qrResolver;

    RegistrationCommandController(CommandBus commandBus, RegistrationQrResolver qrResolver) {
        this.commandBus = commandBus;
        this.qrResolver = qrResolver;
    }

    @Idempotent
    @PostMapping
    ResponseEntity<Void> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @RequestBody RegisterWorkshopRequest request) {
        UUID registrationId = UUID.randomUUID();
        var command = new RegisterWorkshopCommand(registrationId, request.workshopId(), principal.userId());
        commandBus.execute(command);
        return ResponseEntity.created(URI.create("/api/v1/registrations/" + registrationId)).build();
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id,
                                @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        var command = new CancelRegistrationCommand(id, principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @CanVerifyRegistrations
    @PostMapping("/verify")
    ResponseEntity<Void> verify(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @RequestBody VerifyRegistrationRequest request) {
        // Thin QR seam (Epic 3C, Slice A — fixture): the qrReference is opaque input resolved here to
        // a registrationId; the handler never sees the QR itself. Real resolver → Slice B (OQ-3C-6).
        UUID registrationId = qrResolver.resolveRegistrationId(request.qrReference());
        var command = new VerifyRegistrationCommand(registrationId, principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    record RegisterWorkshopRequest(UUID workshopId) {
    }

    record VerifyRegistrationRequest(String qrReference) {
    }
}