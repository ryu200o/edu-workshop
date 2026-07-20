package io.github.ryu200o.eduworkshop.room.internal.domain.model.policy;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

/**
 * Domain-owned policy for the global room-uniqueness invariant.
 *
 * <p>The invariant — a room must be unique within its physical location by <em>both</em> its
 * {@code (building, floor, code)} coordinate and its {@code (building, floor, name)} pair — is a
 * set-based rule that an aggregate cannot self-prove. This interface expresses the rule in pure domain
 * vocabulary; the IO-backed implementation lives in the infrastructure layer and is injected from
 * outside (Dependency Inversion / Hexagonal). The aggregate remains IO-free and depends only on this
 * interface, never on a repository or outbound port.</p>
 */
public interface RoomUniquenessPolicy {

    /**
     * @return {@code true} when NO other room occupies the {@code (location, code)} coordinate.
     */
    boolean isCodeUnique(RoomLocation location, RoomCode code);

    /**
     * @return {@code true} when NO other room occupies the {@code (location, name)} pair.
     */
    boolean isNameUnique(RoomLocation location, RoomName name);
}
