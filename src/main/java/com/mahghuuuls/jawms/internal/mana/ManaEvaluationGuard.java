package com.mahghuuuls.jawms.internal.mana;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Prevents provider evaluation from mutating or recursively invalidating JAWMS state. */
public final class ManaEvaluationGuard {

    private static final ThreadLocal<Set<UUID>> EVALUATING =
            ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Boolean> VERIFYING = new ThreadLocal<>();

    private ManaEvaluationGuard() {
    }

    public static Scope begin(UUID playerId) {
        requireNotVerifying();
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
        return isVerifying() || EVALUATING.get().contains(playerId);
    }

    public static boolean isEvaluatingOnCurrentThread() {
        return isVerifying() || !EVALUATING.get().isEmpty();
    }

    static VerificationScope beginVerification() {
        requireNotVerifying();
        VERIFYING.set(Boolean.TRUE);
        return new VerificationScope();
    }

    static boolean isVerifying() {
        return VERIFYING.get() != null;
    }

    static void requireNotVerifying() {
        if (isVerifying()) {
            throw new IllegalStateException("JAWMS first-acceptance verification is read-only.");
        }
    }

    static final class VerificationScope implements AutoCloseable {
        private VerificationScope() {
        }

        @Override
        public void close() {
            VERIFYING.remove();
        }
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
