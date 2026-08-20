package com.mahghuuuls.jawms.api;

/**
 * Immutable view of the accepted JAWMS restoration values for ordinary
 * Wizardry mana flasks. An unavailable snapshot must not be interpreted as
 * three zero-value flasks.
 */
public final class ConfiguredFlaskSnapshot {

    /** Revision marker used when accepted gameplay-rules identity is unavailable. */
    public static final long UNKNOWN_REVISION = -1L;

    private static final ConfiguredFlaskSnapshot UNAVAILABLE =
            new ConfiguredFlaskSnapshot(false, 0, 0, 0, UNKNOWN_REVISION);

    private final boolean available;
    private final int smallRestoration;
    private final int mediumRestoration;
    private final int largeRestoration;
    private final long revision;

    private ConfiguredFlaskSnapshot(
            boolean available,
            int smallRestoration,
            int mediumRestoration,
            int largeRestoration,
            long revision
    ) {
        this.available = available;
        this.smallRestoration = smallRestoration;
        this.mediumRestoration = mediumRestoration;
        this.largeRestoration = largeRestoration;
        this.revision = revision;
    }

    /** Returns the shared value used when no accepted rules snapshot is available. */
    public static ConfiguredFlaskSnapshot unavailable() {
        return UNAVAILABLE;
    }

    /**
     * Creates an available accepted-rules view. Revisions identify accepted
     * publications only; they are not clocks and are connection-local on the client.
     */
    public static ConfiguredFlaskSnapshot available(
            int smallRestoration,
            int mediumRestoration,
            int largeRestoration,
            long revision
    ) {
        if (smallRestoration < 0 || mediumRestoration < 0 || largeRestoration < 0) {
            throw new IllegalArgumentException("Configured flask values must be non-negative.");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Revision must be non-negative.");
        }
        return new ConfiguredFlaskSnapshot(
                true,
                smallRestoration,
                mediumRestoration,
                largeRestoration,
                revision
        );
    }

    public boolean isAvailable() {
        return available;
    }

    public int getSmallRestoration() {
        return smallRestoration;
    }

    public int getMediumRestoration() {
        return mediumRestoration;
    }

    public int getLargeRestoration() {
        return largeRestoration;
    }

    public boolean hasKnownRevision() {
        return revision != UNKNOWN_REVISION;
    }

    /** Returns the accepted rules revision, or {@link #UNKNOWN_REVISION}. */
    public long getRevision() {
        return revision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfiguredFlaskSnapshot)) {
            return false;
        }
        ConfiguredFlaskSnapshot that = (ConfiguredFlaskSnapshot) other;
        return available == that.available
                && smallRestoration == that.smallRestoration
                && mediumRestoration == that.mediumRestoration
                && largeRestoration == that.largeRestoration
                && revision == that.revision;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(available);
        result = 31 * result + smallRestoration;
        result = 31 * result + mediumRestoration;
        result = 31 * result + largeRestoration;
        result = 31 * result + (int) (revision ^ (revision >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ConfiguredFlaskSnapshot{available=" + available
                + ", smallRestoration=" + smallRestoration
                + ", mediumRestoration=" + mediumRestoration
                + ", largeRestoration=" + largeRestoration
                + ", revision=" + (hasKnownRevision() ? Long.toString(revision) : "unknown")
                + '}';
    }
}
