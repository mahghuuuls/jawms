package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;

/** Immutable outcome of one accepted or rejected bounded mana mutation. */
public final class ManaMutationResult {

    private final boolean successful;
    private final int requestedAmount;
    private final int actualDelta;
    private final ResourceLocation cause;
    private final ManaPublicState oldState;
    private final ManaPublicState newState;

    public ManaMutationResult(
            boolean successful,
            int requestedAmount,
            int actualDelta,
            ResourceLocation cause,
            ManaPublicState oldState,
            ManaPublicState newState
    ) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Requested amount must be non-negative.");
        }
        if (cause == null || oldState == null || newState == null) {
            throw new NullPointerException("Mutation result fields must not be null.");
        }
        this.successful = successful;
        this.requestedAmount = requestedAmount;
        this.actualDelta = actualDelta;
        this.cause = cause;
        this.oldState = oldState;
        this.newState = newState;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public int getRequestedAmount() {
        return requestedAmount;
    }

    public int getActualDelta() {
        return actualDelta;
    }

    public int getActualAmount() {
        return Math.abs(actualDelta);
    }

    public ResourceLocation getCause() {
        return cause;
    }

    public ManaPublicState getOldState() {
        return oldState;
    }

    public ManaPublicState getNewState() {
        return newState;
    }
}
