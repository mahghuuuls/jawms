package com.mahghuuuls.jawms.internal.modifier;

/** Resolved profile and the exact numeric stages produced by one resolver pass. */
final class ManaProfileCalculation {

    private final ResolvedManaProfile profile;
    private final NumericAggregation maximumMana;
    private final NumericAggregation regeneration;
    private final NumericAggregation lockout;

    ManaProfileCalculation(
            ResolvedManaProfile profile,
            NumericAggregation maximumMana,
            NumericAggregation regeneration,
            NumericAggregation lockout
    ) {
        this.profile = profile;
        this.maximumMana = maximumMana;
        this.regeneration = regeneration;
        this.lockout = lockout;
    }

    ResolvedManaProfile getProfile() {
        return profile;
    }

    NumericAggregation getMaximumMana() {
        return maximumMana;
    }

    NumericAggregation getRegeneration() {
        return regeneration;
    }

    NumericAggregation getLockout() {
        return lockout;
    }
}
