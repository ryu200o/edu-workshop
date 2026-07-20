package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Dedicated infrastructure component translating persistence-level exceptions into the ubiquitous
 * language of the Room domain. All JPA / Hibernate / H2-specific exception parsing lives here so the
 * write adapter stays focused on persistence orchestration. Constraint names, Hibernate APIs and H2
 * workarounds are implementation details and never leak out of this class.
 */
@Component
class JpaRoomPersistenceExceptionTranslator {

    private static final String CONSTRAINT_BUILDING_FLOOR_NAME = "uk_rooms_building_floor_name";
    private static final String CONSTRAINT_BUILDING_FLOOR_CODE = "uk_rooms_building_floor_code";

    RoomDomainException translate(DataIntegrityViolationException ex, Room room) {
        String constraint = extractConstraint(ex);

        if (CONSTRAINT_BUILDING_FLOOR_NAME.equalsIgnoreCase(constraint)) {
            return new DuplicateRoomNameException(room.location(), room.name());
        }

        if (CONSTRAINT_BUILDING_FLOOR_CODE.equalsIgnoreCase(constraint)) {
            return new DuplicateRoomCodeException(room.location(), room.code());
        }

        return new RoomDomainException(
                String.format("Cannot save room at location '%s'. " +
                                "A conflict was detected with an existing room (code or name). " +
                                "Please check the information and try again.",
                        room.location()),
                ex
        );
    }

    private static String extractConstraint(DataIntegrityViolationException ex) {
        if (ex == null) {
            return null;
        }

        Throwable rootCause = ex.getMostSpecificCause();

        // Preferred: the constraint name reported by the ORM (reliable on real DBs via Hibernate).
        if (rootCause instanceof org.hibernate.exception.ConstraintViolationException cve
                && cve.getConstraintName() != null) {
            return cve.getConstraintName();
        }

        // Fallback: some drivers (e.g. H2) omit the constraint name, but the violation message still
        // contains it (e.g. "PUBLIC.UK_ROOMS_BUILDING_FLOOR_CODE ..."). Match it so the gate stays
        // accurate across providers.
        String message = rootCause.getMessage();
        if (message != null) {
            if (message.toUpperCase().contains(CONSTRAINT_BUILDING_FLOOR_NAME.toUpperCase())) {
                return CONSTRAINT_BUILDING_FLOOR_NAME;
            }
            if (message.toUpperCase().contains(CONSTRAINT_BUILDING_FLOOR_CODE.toUpperCase())) {
                return CONSTRAINT_BUILDING_FLOOR_CODE;
            }
        }

        return null;
    }
}
