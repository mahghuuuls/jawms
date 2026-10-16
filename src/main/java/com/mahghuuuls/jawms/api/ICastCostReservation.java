package com.mahghuuuls.jawms.api;

/** One fresh, protected, same-tick provider claim; never share an active handle. */
public interface ICastCostReservation {
    /** Pure validity check; must not reevaluate cost or mutate the claim. */
    boolean isValid();

    /** Resolve once using actual payment, not an exhaustion prediction. */
    CastCostSettlement settle();

    /** Idempotent cleanup without payment; never refund an already settled claim. */
    void release(CastCostReleaseReason reason);
}
