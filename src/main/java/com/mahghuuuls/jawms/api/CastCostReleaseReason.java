package com.mahghuuuls.jawms.api;

/** Why an unresolved provider claim is being closed without payment. */
public enum CastCostReleaseReason {
    PREPARATION_REJECTED, INVALID_BEFORE_EFFECT, FALSE_EFFECT, EFFECT_EXCEPTION,
    INVALID_AFTER_EFFECT, EARLIER_SETTLEMENT_FAILURE, ABANDONED_CALL, LIFECYCLE_CANCELLATION
}
