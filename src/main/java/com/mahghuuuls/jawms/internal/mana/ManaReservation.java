package com.mahghuuuls.jawms.internal.mana;

/**
 * Short-lived internal claim on player mana during one synchronous spell cast.
 */
public final class ManaReservation {

    private final ManaState owner;
    private final int amount;
    private final Object generation;
    private boolean active = true;
    private boolean committed;

    ManaReservation(ManaState owner, int amount) {
        this.owner = owner;
        this.amount = amount;
        this.generation = owner.reservationGeneration();
    }

    public int getAmount() {
        return amount;
    }

    boolean belongsTo(ManaState state) {
        return owner == state;
    }

    boolean isActive() {
        return active && isCurrentGeneration();
    }

    public boolean isCurrentGeneration() {
        return generation == owner.reservationGeneration();
    }

    public boolean matchesGeneration(ManaState state) {
        return owner == state && isCurrentGeneration();
    }

    public boolean sharesGeneration(ManaReservation other) {
        return owner == other.owner && generation == other.generation;
    }

    void resolve() {
        if (!active) {
            throw new IllegalStateException("Mana reservation was already resolved.");
        }
        active = false;
    }

    void commit() {
        resolve();
        committed = true;
    }

    public boolean wasCommitted() {
        return committed;
    }

    void ownerRelease() {
        owner.release(this);
    }
}
