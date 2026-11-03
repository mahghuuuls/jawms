package com.mahghuuuls.jawms.api;

/**
 * Optional extra permission/payment for player wand effects, not scrolls or procs.
 * Callbacks run synchronously on the logical server. Preparation must not pay,
 * execute an effect, start payment delay, or retain borrowed player/spell objects.
 * Casting from any callback is rejected. Resource owners must protect open claims.
 */
public interface ICastCostParticipant {
    CastCostPreparation prepare(CastAttemptContext context);

    /**
     * Receives an immutable view of coordination through this notification boundary.
     * Earlier debits remain recorded if a subsequent callback fails or invalidates the
     * attempt. The notification is not replayed, and ordinary success processing can
     * still be suppressed after this callback returns.
     */
    default void onAttemptResolved(CastAttemptOutcome outcome) {
    }
}
