package com.mahghuuuls.jawms.api.client;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Posted on the physical client immediately before JAWMS may draw its built-in
 * mana bar. Cancel this event to suppress only that built-in draw pass.
 */
@Cancelable
public final class ManaHudRenderEvent extends Event {

    private final ClientManaSnapshot snapshot;
    private final float partialTicks;
    private final int scaledWidth;
    private final int scaledHeight;

    public ManaHudRenderEvent(
            ClientManaSnapshot snapshot,
            float partialTicks,
            int scaledWidth,
            int scaledHeight
    ) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        if (!Float.isFinite(partialTicks)) {
            throw new IllegalArgumentException("Partial ticks must be finite.");
        }
        if (scaledWidth < 0 || scaledHeight < 0) {
            throw new IllegalArgumentException("Scaled dimensions must be non-negative.");
        }
        this.snapshot = snapshot;
        this.partialTicks = partialTicks;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
    }

    public ClientManaSnapshot getSnapshot() {
        return snapshot;
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
