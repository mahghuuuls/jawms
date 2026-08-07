package com.mahghuuuls.jawms.api.client;

/**
 * Immutable read-only view of the synchronized local player's public mana.
 * An unavailable snapshot must not be interpreted as zero mana.
 */
public final class ClientManaSnapshot {

    private static final ClientManaSnapshot UNAVAILABLE =
            new ClientManaSnapshot(false, 0, 0, 0.0D);

    private final boolean available;
    private final int currentMana;
    private final int maximumMana;
    private final double effectiveRegeneration;

    private ClientManaSnapshot(
            boolean available,
            int currentMana,
            int maximumMana,
            double effectiveRegeneration
    ) {
        this.available = available;
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.effectiveRegeneration = effectiveRegeneration;
    }

    /** Returns the shared snapshot used before synchronization and after disconnect. */
    public static ClientManaSnapshot unavailable() {
        return UNAVAILABLE;
    }

    /**
     * Creates an available synchronized view.
     *
     * @throws IllegalArgumentException when the values cannot represent a valid
     *                                  public mana state
     */
    public static ClientManaSnapshot available(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration
    ) {
        if (maximumMana < 1) {
            throw new IllegalArgumentException("Maximum mana must be positive.");
        }
        if (currentMana < 0 || currentMana > maximumMana) {
            throw new IllegalArgumentException("Current mana must be within maximum mana.");
        }
        if (!Double.isFinite(effectiveRegeneration) || effectiveRegeneration < 0.0D) {
            throw new IllegalArgumentException("Effective regeneration must be finite and non-negative.");
        }
        return new ClientManaSnapshot(
                true,
                currentMana,
                maximumMana,
                effectiveRegeneration
        );
    }

    public boolean isAvailable() {
        return available;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getMaximumMana() {
        return maximumMana;
    }

    public double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }
}
