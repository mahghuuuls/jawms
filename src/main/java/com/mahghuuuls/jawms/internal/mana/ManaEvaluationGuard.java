package com.mahghuuuls.jawms.internal.mana;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Prevents provider evaluation from mutating or recursively invalidating JAWMS state. */
public final class ManaEvaluationGuard {

    private static final ThreadLocal<Set<UUID>> EVALUATING =
            ThreadLocal.withInitial(HashSet::new);

    private ManaEvaluationGuard() {
    }

    public static Scope begin(UUID playerId) {
        if (playerId == null) {
            throw new NullPointerException("playerId");
        }
        Set<UUID> evaluating = EVALUATING.get();
        if (!evaluating.add(playerId)) {
            throw new IllegalStateException("JAWMS modifier evaluation is already active for this player.");
        }
        return new Scope(playerId);
    }

    public static boolean isEvaluating(UUID playerId) {
        return EVALUATING.get().contains(playerId);
    }

    public static final class Scope implements AutoCloseable {

        private final UUID playerId;
        private boolean active = true;

        private Scope(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            Set<UUID> evaluating = EVALUATING.get();
            evaluating.remove(playerId);
            if (evaluating.isEmpty()) {
                EVALUATING.remove();
            }
        }
    }
}
