package com.mahghuuuls.jawms.api;

import java.util.Objects;

/**
 * Immutable numeric stages used to resolve maximum mana, regeneration, or lockout.
 * The authoritative subtotal is the recorded {@code nonNegativeSubtotal}; the
 * resolver applies flat values one at a time with saturation, so extreme inputs
 * cannot always reconstruct it from {@code configuredBase + flatTotal}. The
 * resolved value then applies the percentage increase and reduction factor.
 * Type-specific flooring or tick conversion is reported by the enclosing
 * {@link ManaProfileExplanation}.
 */
public final class ManaProfileAggregation {

    private final double configuredBase;
    private final double flatTotal;
    private final double nonNegativeSubtotal;
    private final double increasePercentage;
    private final double reductionFactor;
    private final double resolvedValue;

    public ManaProfileAggregation(
            double configuredBase,
            double flatTotal,
            double nonNegativeSubtotal,
            double increasePercentage,
            double reductionFactor,
            double resolvedValue
    ) {
        requireFinite(configuredBase, "configuredBase");
        requireFinite(flatTotal, "flatTotal");
        requireNonNegativeFinite(nonNegativeSubtotal, "nonNegativeSubtotal");
        requireNonNegativeFinite(increasePercentage, "increasePercentage");
        requireNonNegativeFinite(reductionFactor, "reductionFactor");
        requireNonNegativeFinite(resolvedValue, "resolvedValue");
        this.configuredBase = configuredBase;
        this.flatTotal = flatTotal;
        this.nonNegativeSubtotal = nonNegativeSubtotal;
        this.increasePercentage = increasePercentage;
        this.reductionFactor = reductionFactor;
        this.resolvedValue = resolvedValue;
    }

    public double getConfiguredBase() {
        return configuredBase;
    }

    public double getFlatTotal() {
        return flatTotal;
    }

    public double getNonNegativeSubtotal() {
        return nonNegativeSubtotal;
    }

    public double getIncreasePercentage() {
        return increasePercentage;
    }

    public double getReductionFactor() {
        return reductionFactor;
    }

    public double getResolvedValue() {
        return resolvedValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManaProfileAggregation)) {
            return false;
        }
        ManaProfileAggregation that = (ManaProfileAggregation) other;
        return Double.compare(configuredBase, that.configuredBase) == 0
                && Double.compare(flatTotal, that.flatTotal) == 0
                && Double.compare(nonNegativeSubtotal, that.nonNegativeSubtotal) == 0
                && Double.compare(increasePercentage, that.increasePercentage) == 0
                && Double.compare(reductionFactor, that.reductionFactor) == 0
                && Double.compare(resolvedValue, that.resolvedValue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configuredBase, flatTotal, nonNegativeSubtotal,
                increasePercentage, reductionFactor, resolvedValue);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0D) {
            throw new IllegalArgumentException(name + " must be non-negative.");
        }
    }
}
