package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaProfileAggregation;

/** Internal arithmetic trace emitted by the authoritative resolver. */
final class NumericAggregation {

    private final double configuredBase;
    private final double flatTotal;
    private final double nonNegativeSubtotal;
    private final double increasePercentage;
    private final double reductionFactor;
    private final double resolvedValue;

    NumericAggregation(
            double configuredBase,
            double flatTotal,
            double nonNegativeSubtotal,
            double increasePercentage,
            double reductionFactor,
            double resolvedValue
    ) {
        this.configuredBase = configuredBase;
        this.flatTotal = flatTotal;
        this.nonNegativeSubtotal = nonNegativeSubtotal;
        this.increasePercentage = increasePercentage;
        this.reductionFactor = reductionFactor;
        this.resolvedValue = resolvedValue;
    }

    ManaProfileAggregation toPublicAggregation() {
        return new ManaProfileAggregation(
                configuredBase, flatTotal, nonNegativeSubtotal,
                increasePercentage, reductionFactor, resolvedValue
        );
    }
}
