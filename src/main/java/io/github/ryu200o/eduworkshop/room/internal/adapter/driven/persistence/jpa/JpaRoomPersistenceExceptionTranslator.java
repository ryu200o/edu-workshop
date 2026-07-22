package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomPersistenceException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomNameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Converts persistence-provider failures into exceptions meaningful to upper layers.
 * Business conflicts are translated into Domain exceptions;
 * unexpected persistence failures become Application persistence exceptions.
 * It reasons in terms of <em>business conflicts</em> (a duplicate room
 * name, a duplicate room code) rather than database metadata. All JPA / Hibernate / H2 detection
 * mechanics are encapsulated behind business-oriented helpers and never surface as constraint names.
 */
@Component
class JpaRoomPersistenceExceptionTranslator {

    private static final String CONSTRAINT_BUILDING_FLOOR_NAME = "uk_rooms_building_floor_name";
    private static final String CONSTRAINT_BUILDING_FLOOR_CODE = "uk_rooms_building_floor_code";

     RuntimeException translate(DataIntegrityViolationException ex, Room room) {
        if (representsDuplicateRoomName(ex)) {
            return new DuplicateRoomNameException(room.location(), room.name());
        }

        if (representsDuplicateRoomCode(ex)) {
            return new DuplicateRoomCodeException(room.location(), room.code());
        }

        return new RoomPersistenceException(
                String.format("Cannot save room at location '%s'. " +
                                "A conflict was detected with an existing room (code or name). " +
                                "Please check the information and try again.",
                        room.location()),
                ex
        );
    }

    // ====================== BUSINESS-CONFLICT DETECTION ======================

    private static boolean representsDuplicateRoomName(DataIntegrityViolationException ex) {
        return violatesConstraint(ex, CONSTRAINT_BUILDING_FLOOR_NAME);
    }

    private static boolean representsDuplicateRoomCode(DataIntegrityViolationException ex) {
        return violatesConstraint(ex, CONSTRAINT_BUILDING_FLOOR_CODE);
    }

    // ====================== TECHNICAL DETECTION MECHANISM ======================

    private static boolean violatesConstraint(DataIntegrityViolationException ex, String constraintName) {
        if (ex == null) {
            return false;
        }

        Throwable rootCause = ex.getMostSpecificCause();

        // Preferred: the constraint name reported by the ORM (reliable on real DBs via Hibernate).
        if (rootCause instanceof org.hibernate.exception.ConstraintViolationException cve
                && cve.getConstraintName() != null) {
            return constraintName.equalsIgnoreCase(cve.getConstraintName());
        }

        // Fallback: some drivers (e.g. H2) omit the constraint name, but the violation message still
        // contains it (e.g. "PUBLIC.UK_ROOMS_BUILDING_FLOOR_CODE ..."). Match it so the gate stays
        // accurate across providers.
        String message = rootCause.getMessage();
        return message != null && message.toUpperCase().contains(constraintName.toUpperCase());
    }
}
