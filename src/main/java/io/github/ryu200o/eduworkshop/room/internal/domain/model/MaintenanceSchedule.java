package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.InvalidMaintenanceScheduleException;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a scheduled maintenance window for a {@link Room}. An immutable entity that captures
 * the time window, reason, and operator of a planned maintenance activity.
 *
 * <p>Invariants:</p>
 * <ul>
 *   <li>{@code reason} must be at least 10 characters long (audit transparency).</li>
 *   <li>If {@code endTime} is not null, it must be after {@code startTime}.</li>
 *   <li>If {@code endTime} is not null, it must be in the future relative to {@code now}.</li>
 * </ul>
 */
public final class MaintenanceSchedule {

    private final MaintenanceId id;
    private final RoomId roomId;
    private final Instant startTime;
    private final Instant endTime; // nullable = indefinite maintenance
    private final String reason;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MaintenanceSchedule(MaintenanceId id, RoomId roomId, Instant startTime, Instant endTime,
                                String reason, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "MaintenanceId cannot be null");
        this.roomId = Objects.requireNonNull(roomId, "RoomId cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime cannot be null");
        this.endTime = endTime; // nullable
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    /**
     * Factory that creates a new MaintenanceSchedule. Validates local invariants.
     *
     * @param id        the schedule identity
     * @param roomId    the room this schedule belongs to
     * @param startTime the maintenance window start (must not be null)
     * @param endTime   the maintenance window end (null = indefinite)
     * @param reason    the maintenance reason (must be at least 10 characters)
     * @param createdBy the operator who created this schedule
     * @param now       the current instant
     * @return a new MaintenanceSchedule
     * @throws InvalidMaintenanceScheduleException if invariants are violated
     */
    public static MaintenanceSchedule create(MaintenanceId id, RoomId roomId,
                                             Instant startTime, Instant endTime,
                                             String reason, String createdBy, Instant now) {
        if (reason == null || reason.length() < 10) {
            throw new InvalidMaintenanceScheduleException(
                    "Maintenance reason must be at least 10 characters long, got: " +
                    (reason == null ? "null" : reason.length()));
        }
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new InvalidMaintenanceScheduleException(
                    "Maintenance endTime (" + endTime + ") must be after startTime (" + startTime + ")");
        }
        // ADR 0015: reject scheduling maintenance whose window has already ended.
        if (endTime != null && !endTime.isAfter(now)) {
            throw new InvalidMaintenanceScheduleException(
                    "Maintenance endTime (" + endTime + ") must be in the future; a past maintenance window cannot be scheduled.");
        }
        return new MaintenanceSchedule(id, roomId, startTime, endTime, reason, createdBy, now, now);
    }

    /**
     * Reconstitutes an existing schedule from persistence. No invariant checks — no spurious
     * re-validation on read.
     */
    public static MaintenanceSchedule reconstruct(MaintenanceId id, RoomId roomId,
                                                  Instant startTime, Instant endTime,
                                                  String reason, String createdBy,
                                                  Instant createdAt, Instant updatedAt) {
        return new MaintenanceSchedule(id, roomId, startTime, endTime, reason, createdBy, createdAt, updatedAt);
    }

    public MaintenanceId id() {
        return id;
    }

    public RoomId roomId() {
        return roomId;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public String reason() {
        return reason;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
