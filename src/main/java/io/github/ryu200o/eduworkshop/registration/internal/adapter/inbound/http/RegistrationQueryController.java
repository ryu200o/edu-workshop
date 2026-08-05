package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.GetMyRegistrationsQuery;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Registration READ side (Query). Talks exclusively to the shared
 * {@link QueryBus}. The acting user is a logical reference (no User module — SA+PO decision) and
 * arrives via the {@code X-User-Id} header in Dev/Test.
 *
 * <p>The optional {@code status} filter accepts {@code REGISTERED}, {@code CANCELLED} and
 * {@code REFUNDED} (each learner sees only their own rows, so a refunded order is a normal,
 * learner-selectable booking state). When omitted the full booking history is returned.
 * Error handling is centralized in {@link RegistrationExceptionAdvice}.</p>
 */
@RestController
@RequestMapping("/api/v1/registrations")
class RegistrationQueryController {

    private final QueryBus queryBus;

    RegistrationQueryController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping
    ResponseEntity<List<MyRegistrationView>> myBookings(@RequestHeader("X-User-Id") UUID userId,
                                                        @RequestParam(value = "status", required = false) MyRegistrationStatus status) {
        List<MyRegistrationView> result = queryBus.execute(new GetMyRegistrationsQuery(userId, status));
        return ResponseEntity.ok(result);
    }
}
