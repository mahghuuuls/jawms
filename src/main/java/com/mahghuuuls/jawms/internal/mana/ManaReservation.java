package com.mahghuuuls.jawms.internal.mana;

/**
 * Short-lived internal claim on player mana during one synchronous spell cast.
 */
public final class ManaReservation {

    private final ManaState owner;
    private final int amount;
    private boolean active = true;

    ManaReservation(ManaState owner, int amount) {
        this.owner = owner;
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    boolean belongsTo(ManaState state) {
        return owner == state;
    }

    boolean isActive() {
        return active;
    }

    void resolve() {
        if (!active) {
            throw new IllegalStateException("Mana reservation was already resolved.");
        }
        active = false;
    }

    void ownerRelease() {
        owner.release(this);
    }
}
