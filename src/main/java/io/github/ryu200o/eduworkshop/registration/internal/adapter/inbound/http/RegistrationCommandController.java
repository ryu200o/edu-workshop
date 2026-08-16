package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.CancelRegistrationCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.RegisterWorkshopCommand;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command.VerifyRegistrationCommand;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driving HTTP adapter for the Registration WRITE side (Command). Accepts only state-changing HTTP
 * methods and talks exclusively to the shared {@link CommandBus}. The acting user is a logical
 * reference (there is no User module — SA+PO decision) and arrives via the {@code X-User-Id} header
 * in Dev/Test; production will derive it from the authenticated context (JWT filter) later.
 * Error handling is centralized in {@link RegistrationExceptionAdvice}.
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

    @PostMapping
    ResponseEntity<RegisterWorkshopCommand.Result> register(@RequestHeader("X-User-Id") UUID userId,
                                                            @RequestBody RegisterWorkshopRequest request) {
        var command = new RegisterWorkshopCommand(request.workshopId(), userId);
        RegisterWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<CancelRegistrationCommand.Result> cancel(@PathVariable UUID id,
                                                            @RequestHeader("X-User-Id") UUID userId) {
        var command = new CancelRegistrationCommand(id, userId);
        CancelRegistrationCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify")
    ResponseEntity<VerifyRegistrationCommand.Result> verify(@RequestHeader("X-Actor-Role") String role,
                                                            @RequestHeader("X-User-Id") UUID userId,
                                                            @RequestBody VerifyRegistrationRequest request) {
        // Thin QR seam (Epic 3C, Slice A — fixture): the qrReference is opaque input resolved here to
        // a registrationId; the handler never sees the QR itself. Real resolver → Slice B (OQ-3C-6).
        UUID registrationId = qrResolver.resolveRegistrationId(request.qrReference());
        var command = new VerifyRegistrationCommand(registrationId, userId, role);
        VerifyRegistrationCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    record RegisterWorkshopRequest(UUID workshopId) {
    }

    record VerifyRegistrationRequest(String qrReference) {
    }
}
