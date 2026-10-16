package com.mahghuuuls.jawms.api;

/**
 * Optional extra permission/payment for player wand effects, not scrolls or procs.
 * Callbacks run synchronously on the logical server. Preparation must not pay,
 * execute an effect, start payment delay, or retain borrowed player/spell objects.
 * Casting from any callback is rejected. Resource owners must protect open claims.
 */
public interface ICastCostParticipant {
    CastCostPreparation prepare(CastAttemptContext context);

    /** Final coordination result, not merely this participant's own debit. */
    default void onAttemptResolved(CastAttemptOutcome outcome) {
    }
}
