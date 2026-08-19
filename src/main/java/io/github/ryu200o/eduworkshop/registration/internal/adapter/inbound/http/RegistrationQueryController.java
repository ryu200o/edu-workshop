package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.GetMyRegistrationsQuery;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Driving HTTP adapter for the Registration READ side (Query). Talks exclusively to the shared
 * {@link QueryBus}. The acting learner is read from the {@link AuthenticatedPrincipal} (IAM Slice 5 —
 * the {@code X-User-Id} header is gone), so each caller sees only their own rows.
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
    ResponseEntity<List<MyRegistrationView>> myBookings(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                        @RequestParam(value = "status", required = false) MyRegistrationStatus status) {
        List<MyRegistrationView> result = queryBus.execute(new GetMyRegistrationsQuery(principal.userId(), status));
        return ResponseEntity.ok(result);
    }
}