package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import java.util.UUID;

/**
 * Value Object capturing the room a workshop is planned/reserved for, and a point-in-time display snapshot.
 *
 * <p>Per ADR 0007 the Workshop module is decoupled from Room: it stores only the logical {@code roomId}
 * (UUID, no physical FK) plus two read-only display snapshots ({@code roomNameSnapshot},
 * {@code roomLocationSnapshot}) for UI convenience. The snapshots are plain strings — display only, never
 * reverse-parsed. They are supplied to the domain as plain values so the aggregate stays free of IO; in
 * later slices they will be populated from a {@code RoomExposeAPI} DTO by the Application layer.</p>
 */
public record RoomReference(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot) {

    public RoomReference {
        if (roomId == null) {
            throw new WorkshopDomainException("Room reference must carry a room id.");
        }
    }

    public static RoomReference of(UUID roomId, String roomNameSnapshot, String roomLocationSnapshot) {
        return new RoomReference(roomId, roomNameSnapshot, roomLocationSnapshot);
    }
}
