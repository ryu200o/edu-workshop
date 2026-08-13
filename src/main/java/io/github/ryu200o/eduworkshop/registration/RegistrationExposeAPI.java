package io.github.ryu200o.eduworkshop.registration;

import java.util.List;
import java.util.UUID;

/**
 * Public inter-module communication interface for the registration module.
 * This is the only surface exposed to other modules.
 */
public interface RegistrationExposeAPI {

    /**
     * Counts the total active ({@code REGISTERED}) seats across multiple workshops.
     * Used by upper-layer modules (e.g. FacilityOps) to size the impact of a maintenance window.
     *
     * @param workshopIds the workshop ids to count registrations for
     * @return total number of active registrations across all specified workshops
     */
    int countActiveByWorkshopIds(List<UUID> workshopIds);

    /**
     * Read-only predicate: is the learner's registration for the workshop {@code VERIFIED}?
     * Used by the Attendance module to gate attendance recording (SA directive) — a learner must
     * have a verified seat before attendance is recorded. Attendance <em>only reads</em> through
     * this path; only the Registration module mutates Registration state.
     *
     * @return {@code true} if the (workshop, student) registration exists and its status is
     *         {@code VERIFIED}; {@code false} otherwise (no row, or not yet verified)
     */
    boolean isVerified(UUID workshopId, UUID studentId);
}
