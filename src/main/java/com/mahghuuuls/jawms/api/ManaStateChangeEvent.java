package com.mahghuuuls.jawms.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Posted on the logical server after a meaningful JAWMS state transition commits. */
public final class ManaStateChangeEvent extends Event {

    private final EntityPlayer player;
    private final ManaPublicState oldState;
    private final ManaPublicState newState;
    private final int actualManaDelta;
    private final ResourceLocation cause;

    /**
     * Forge 1.12 event-bus discovery constructor. The event bus creates this
     * instance only to locate the listener list; JAWMS never posts it.
     */
    @Deprecated
    public ManaStateChangeEvent() {
        this.player = null;
        this.oldState = null;
        this.newState = null;
        this.actualManaDelta = 0;
        this.cause = null;
    }

    public ManaStateChangeEvent(
            EntityPlayer player,
            ManaPublicState oldState,
            ManaPublicState newState,
            ResourceLocation cause
    ) {
        if (player == null || oldState == null || newState == null || cause == null) {
            throw new NullPointerException("Mana state event fields must not be null.");
        }
        this.player = player;
        this.oldState = oldState;
        this.newState = newState;
        this.actualManaDelta = newState.getCurrentMana() - oldState.getCurrentMana();
        this.cause = cause;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public ManaPublicState getOldState() {
        return oldState;
    }

    public ManaPublicState getNewState() {
        return newState;
    }

    public int getActualManaDelta() {
        return actualManaDelta;
    }

    public ResourceLocation getCause() {
        return cause;
    }
}
