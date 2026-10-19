package com.mahghuuuls.jawms.internal.casting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Owns only captured finish operations, never a stopped-using method or resource claim. */
public final class FinishContinuation {
    public enum Capture { LEGACY, CAPTURED, SUPPRESSED }
    public enum Result { FINISHED, CANCELLED, SUPPRESSED, ALREADY_RESOLVED }
    private BooleanSupplier originalContext;
    private BooleanSupplier ownsContext;
    private final List<Runnable> actions = new ArrayList<>(3);
    private boolean resolved;

    public FinishContinuation(BooleanSupplier originalContext) {
        this(originalContext, originalContext);
    }
    public FinishContinuation(BooleanSupplier originalContext, BooleanSupplier ownsContext) {
        this.originalContext = Objects.requireNonNull(originalContext, "originalContext");
        this.ownsContext = Objects.requireNonNull(ownsContext, "ownsContext");
    }
    public void capture(Runnable action) {
        if (resolved || actions.size() >= 3) throw new IllegalStateException("Finish capture is closed or exceeds its operations");
        actions.add(Objects.requireNonNull(action, "action"));
    }
    public Result resolve(boolean permitted) {
        if (resolved) return Result.ALREADY_RESOLVED;
        resolved = true;
        BooleanSupplier validity = originalContext;
        BooleanSupplier ownership = ownsContext;
        originalContext = null;
        ownsContext = null;
        List<Runnable> captured = new ArrayList<>(actions);
        actions.clear();
        if (!permitted) return Result.SUPPRESSED;
        if (!validity.getAsBoolean()) return Result.CANCELLED;
        for (Runnable action : captured) {
            if (!ownership.getAsBoolean()) return Result.CANCELLED;
            action.run();
        }
        return Result.FINISHED;
    }
}
