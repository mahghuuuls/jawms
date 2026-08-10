package com.mahghuuuls.jawms.api.client;

/** Immutable render-call context for a public mana HUD event. */
public final class ClientHudContext {

    private final boolean creativeModeKnown;
    private final boolean creativeMode;
    private final float partialTicks;
    private final int scaledWidth;
    private final int scaledHeight;

    private ClientHudContext(
            boolean creativeModeKnown,
            boolean creativeMode,
            float partialTicks,
            int scaledWidth,
            int scaledHeight
    ) {
        if (!Float.isFinite(partialTicks)) {
            throw new IllegalArgumentException("Partial ticks must be finite.");
        }
        if (scaledWidth < 0 || scaledHeight < 0) {
            throw new IllegalArgumentException("Scaled dimensions must be non-negative.");
        }
        this.creativeModeKnown = creativeModeKnown;
        this.creativeMode = creativeMode;
        this.partialTicks = partialTicks;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
    }

    /** Creates a context whose captured Creative-mode state is known. */
    public static ClientHudContext known(
            boolean creativeMode,
            float partialTicks,
            int scaledWidth,
            int scaledHeight
    ) {
        return new ClientHudContext(
                true,
                creativeMode,
                partialTicks,
                scaledWidth,
                scaledHeight
        );
    }

    /** Creates a context whose Creative-mode state was not supplied. */
    public static ClientHudContext unknown(
            float partialTicks,
            int scaledWidth,
            int scaledHeight
    ) {
        return new ClientHudContext(false, false, partialTicks, scaledWidth, scaledHeight);
    }

    public boolean isCreativeModeKnown() {
        return creativeModeKnown;
    }

    /**
     * Returns the captured Creative-mode state.
     *
     * @throws IllegalStateException when the state is unknown
     */
    public boolean isCreativeMode() {
        if (!creativeModeKnown) {
            throw new IllegalStateException("Creative mode is unknown in this context.");
        }
        return creativeMode;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }
}
