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
    private final ClientHudContext context;

    /**
     * Retained API 1.2 constructor. Creative-mode state is unknown because it
     * was not part of that contract.
     */
    public ManaHudRenderEvent(
            ClientManaSnapshot snapshot,
            float partialTicks,
            int scaledWidth,
            int scaledHeight
    ) {
        this(
                requireSnapshot(snapshot),
                ClientHudContext.unknown(partialTicks, scaledWidth, scaledHeight)
        );
    }

    /** Creates an event carrying the captured snapshot and render context. */
    public ManaHudRenderEvent(
            ClientManaSnapshot snapshot,
            ClientHudContext context
    ) {
        this.snapshot = requireSnapshot(snapshot);
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
    }

    public ClientManaSnapshot getSnapshot() {
        return snapshot;
    }

    public ClientHudContext getContext() {
        return context;
    }

    public float getPartialTicks() {
        return context.getPartialTicks();
    }

    public int getScaledWidth() {
        return context.getScaledWidth();
    }

    public int getScaledHeight() {
        return context.getScaledHeight();
    }

    private static ClientManaSnapshot requireSnapshot(ClientManaSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        return snapshot;
    }
}
