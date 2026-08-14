package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;

/**
 * Immutable outcome of one accepted or rejected bounded mana mutation.
 *
 * <p>The retained six-argument constructor maps successful results to
 * {@link ManaMutationFailure#NONE} and unsuccessful results to the conservative
 * {@link ManaMutationFailure#REJECTED} reason.</p>
 */
public final class ManaMutationResult {

    private final boolean successful;
    private final ManaMutationFailure failure;
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
        this(
                successful ? ManaMutationFailure.NONE : ManaMutationFailure.REJECTED,
                requestedAmount,
                actualDelta,
                cause,
                oldState,
                newState
        );
    }

    public ManaMutationResult(
            ManaMutationFailure failure,
            int requestedAmount,
            int actualDelta,
            ResourceLocation cause,
            ManaPublicState oldState,
            ManaPublicState newState
    ) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Requested amount must be non-negative.");
        }
        if (failure == null || cause == null || oldState == null || newState == null) {
            throw new NullPointerException("Mutation result fields must not be null.");
        }
        this.successful = failure == ManaMutationFailure.NONE;
        this.failure = failure;
        this.requestedAmount = requestedAmount;
        this.actualDelta = actualDelta;
        this.cause = cause;
        this.oldState = oldState;
        this.newState = newState;
    }

    public boolean isSuccessful() {
        return successful;
    }

    /** Returns {@code NONE} exactly when this result is successful. */
    public ManaMutationFailure getFailure() {
        return failure;
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
