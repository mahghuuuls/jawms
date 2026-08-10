package com.mahghuuuls.jawms.api.client;

/**
 * Immutable read-only view of the synchronized local player's public mana.
 * An unavailable snapshot must not be interpreted as zero mana.
 */
public final class ClientManaSnapshot {

    /** Revision marker used when synchronized revision identity is unavailable. */
    public static final long UNKNOWN_REVISION = -1L;

    private static final ClientManaSnapshot UNAVAILABLE =
            new ClientManaSnapshot(false, 0, 0, 0.0D, UNKNOWN_REVISION);

    private final boolean available;
    private final int currentMana;
    private final int maximumMana;
    private final double effectiveRegeneration;
    private final long revision;

    private ClientManaSnapshot(
            boolean available,
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
    ) {
        this.available = available;
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.effectiveRegeneration = effectiveRegeneration;
        this.revision = revision;
    }

    /**
     * Returns the shared unknown-revision snapshot used before synchronization
     * and after disconnect.
     */
    public static ClientManaSnapshot unavailable() {
        return UNAVAILABLE;
    }

    /**
     * Retained API 1.2 factory for an available value. Its revision is
     * explicitly unknown, so callers must not treat it as synchronized-state
     * identity or freshness.
     *
     * @throws IllegalArgumentException when the values cannot represent a valid
     *                                  public mana state
     */
    public static ClientManaSnapshot available(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration
    ) {
        return availableWithRevision(
                currentMana,
                maximumMana,
                effectiveRegeneration,
                UNKNOWN_REVISION
        );
    }

    /**
     * Creates an available synchronized view with connection-local revision
     * identity. Revisions identify accepted states only; they are not clocks.
     *
     * @throws IllegalArgumentException when the mana values are invalid or the
     *                                  revision is negative
     */
    public static ClientManaSnapshot available(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
    ) {
        if (revision < 0L) {
            throw new IllegalArgumentException("Revision must be non-negative.");
        }
        return availableWithRevision(
                currentMana,
                maximumMana,
                effectiveRegeneration,
                revision
        );
    }

    private static ClientManaSnapshot availableWithRevision(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
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
                effectiveRegeneration,
                revision
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

    /** Returns whether {@link #getRevision()} has synchronized identity. */
    public boolean hasKnownRevision() {
        return revision != UNKNOWN_REVISION;
    }

    /**
     * Returns a non-negative connection-local revision, or
     * {@link #UNKNOWN_REVISION} when it is unknown.
     */
    public long getRevision() {
        return revision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClientManaSnapshot)) {
            return false;
        }
        ClientManaSnapshot that = (ClientManaSnapshot) other;
        return available == that.available
                && currentMana == that.currentMana
                && maximumMana == that.maximumMana
                && Double.compare(effectiveRegeneration, that.effectiveRegeneration) == 0
                && revision == that.revision;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(available);
        result = 31 * result + currentMana;
        result = 31 * result + maximumMana;
        long regenerationBits = Double.doubleToLongBits(effectiveRegeneration);
        result = 31 * result + (int) (regenerationBits ^ (regenerationBits >>> 32));
        result = 31 * result + (int) (revision ^ (revision >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ClientManaSnapshot{available=" + available
                + ", currentMana=" + currentMana
                + ", maximumMana=" + maximumMana
                + ", effectiveRegeneration=" + effectiveRegeneration
                + ", revision=" + (hasKnownRevision() ? Long.toString(revision) : "unknown")
                + '}';
    }
}
