package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotFoundException;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin QR seam (Epic 3C, Slice A — fixture).
 *
 * <p>Per the 3B convention, the {@code qrReference} is an <strong>opaque input</strong> to the
 * inbound adapter; this seam is the only place that interprets it. In Slice A the resolver is a
 * deterministic fixture (no ticket vault): a reference of the form {@code QR-REG-<uuid>} maps to the
 * embedded registration id. The real QR payload / ticket-token resolver (expiry, anti-replay) is a
 * Slice B backlog (OQ-3C-6) — swapping this implementation is the only change needed there, the
 * core handler stays untouched.</p>
 */
@Component
class RegistrationQrResolver {

    private static final String FIXTURE_PREFIX = "QR-REG-";

    /**
     * Resolves a scanned {@code qrReference} to a {@code registrationId}.
     *
     * @throws RegistrationNotFoundException when the reference does not denote a registration
     */
    UUID resolveRegistrationId(String qrReference) {
        if (qrReference == null || !qrReference.startsWith(FIXTURE_PREFIX)) {
            throw new RegistrationNotFoundException("qrReference", String.valueOf(qrReference));
        }
        String raw = qrReference.substring(FIXTURE_PREFIX.length());
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new RegistrationNotFoundException("qrReference", qrReference);
        }
    }
}