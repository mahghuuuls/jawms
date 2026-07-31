package com.mahghuuuls.jawms.api;

import electroblob.wizardry.constants.Element;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable complete contribution to a player's resolved JAWMS profile. */
public final class ManaContribution {

    public static final ManaContribution EMPTY = builder().build();

    private final int flatMaximumMana;
    private final double maximumManaIncrease;
    private final double maximumManaReduction;
    private final double flatRegeneration;
    private final double regenerationIncrease;
    private final double regenerationReduction;
    private final double flatLockoutSeconds;
    private final double lockoutIncrease;
    private final double lockoutReduction;
    private final double globalSpellEfficiency;
    private final Map<Element, Double> elementSpellEfficiency;
    private final boolean regenerateDuringPostCastLockout;
    private final boolean regenerateDuringContinuousCasting;

    private ManaContribution(Builder builder) {
        flatMaximumMana = builder.flatMaximumMana;
        maximumManaIncrease = nonNegative(builder.maximumManaIncrease, "maximumManaIncrease");
        maximumManaReduction = reduction(builder.maximumManaReduction, "maximumManaReduction");
        flatRegeneration = finite(builder.flatRegeneration, "flatRegeneration");
        regenerationIncrease = nonNegative(builder.regenerationIncrease, "regenerationIncrease");
        regenerationReduction = reduction(builder.regenerationReduction, "regenerationReduction");
        flatLockoutSeconds = finite(builder.flatLockoutSeconds, "flatLockoutSeconds");
        lockoutIncrease = nonNegative(builder.lockoutIncrease, "lockoutIncrease");
        lockoutReduction = reduction(builder.lockoutReduction, "lockoutReduction");
        globalSpellEfficiency = nonNegative(
                builder.globalSpellEfficiency,
                "globalSpellEfficiency"
        );
        EnumMap<Element, Double> efficiencies = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Double> entry : builder.elementSpellEfficiency.entrySet()) {
            if (entry.getKey() == null) {
                throw new NullPointerException("elementSpellEfficiency key");
            }
            efficiencies.put(
                    entry.getKey(),
                    nonNegative(entry.getValue(), "elementSpellEfficiency")
            );
        }
        elementSpellEfficiency = Collections.unmodifiableMap(efficiencies);
        regenerateDuringPostCastLockout = builder.regenerateDuringPostCastLockout;
        regenerateDuringContinuousCasting = builder.regenerateDuringContinuousCasting;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getFlatMaximumMana() {
        return flatMaximumMana;
    }

    public double getMaximumManaIncrease() {
        return maximumManaIncrease;
    }

    public double getMaximumManaReduction() {
        return maximumManaReduction;
    }

    public double getFlatRegeneration() {
        return flatRegeneration;
    }

    public double getRegenerationIncrease() {
        return regenerationIncrease;
    }

    public double getRegenerationReduction() {
        return regenerationReduction;
    }

    public double getFlatLockoutSeconds() {
        return flatLockoutSeconds;
    }

    public double getLockoutIncrease() {
        return lockoutIncrease;
    }

    public double getLockoutReduction() {
        return lockoutReduction;
    }

    public double getGlobalSpellEfficiency() {
        return globalSpellEfficiency;
    }

    public Map<Element, Double> getElementSpellEfficiency() {
        return elementSpellEfficiency;
    }

    public double getElementSpellEfficiency(Element element) {
        Double value = elementSpellEfficiency.get(element);
        return value == null ? 0.0D : value;
    }

    public boolean canRegenerateDuringPostCastLockout() {
        return regenerateDuringPostCastLockout;
    }

    public boolean canRegenerateDuringContinuousCasting() {
        return regenerateDuringContinuousCasting;
    }

    public boolean isEmpty() {
        return flatMaximumMana == 0
                && maximumManaIncrease == 0.0D
                && maximumManaReduction == 0.0D
                && flatRegeneration == 0.0D
                && regenerationIncrease == 0.0D
                && regenerationReduction == 0.0D
                && flatLockoutSeconds == 0.0D
                && lockoutIncrease == 0.0D
                && lockoutReduction == 0.0D
                && globalSpellEfficiency == 0.0D
                && elementSpellEfficiency.isEmpty()
                && !regenerateDuringPostCastLockout
                && !regenerateDuringContinuousCasting;
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return value;
    }

    private static double nonNegative(double value, String name) {
        finite(value, name);
        if (value < 0.0D) {
            throw new IllegalArgumentException(name + " must be non-negative.");
        }
        return value;
    }

    private static double reduction(double value, String name) {
        nonNegative(value, name);
        if (value > 100.0D) {
            throw new IllegalArgumentException(name + " must not exceed 100.");
        }
        return value;
    }

    public static final class Builder {

        private int flatMaximumMana;
        private double maximumManaIncrease;
        private double maximumManaReduction;
        private double flatRegeneration;
        private double regenerationIncrease;
        private double regenerationReduction;
        private double flatLockoutSeconds;
        private double lockoutIncrease;
        private double lockoutReduction;
        private double globalSpellEfficiency;
        private final EnumMap<Element, Double> elementSpellEfficiency =
                new EnumMap<>(Element.class);
        private boolean regenerateDuringPostCastLockout;
        private boolean regenerateDuringContinuousCasting;

        private Builder() {
        }

        public Builder flatMaximumMana(int value) {
            flatMaximumMana = value;
            return this;
        }

        public Builder maximumManaIncrease(double value) {
            maximumManaIncrease = value;
            return this;
        }

        public Builder maximumManaReduction(double value) {
            maximumManaReduction = value;
            return this;
        }

        public Builder flatRegeneration(double value) {
            flatRegeneration = value;
            return this;
        }

        public Builder regenerationIncrease(double value) {
            regenerationIncrease = value;
            return this;
        }

        public Builder regenerationReduction(double value) {
            regenerationReduction = value;
            return this;
        }

        public Builder flatLockoutSeconds(double value) {
            flatLockoutSeconds = value;
            return this;
        }

        public Builder lockoutIncrease(double value) {
            lockoutIncrease = value;
            return this;
        }

        public Builder lockoutReduction(double value) {
            lockoutReduction = value;
            return this;
        }

        public Builder globalSpellEfficiency(double value) {
            globalSpellEfficiency = value;
            return this;
        }

        public Builder elementSpellEfficiency(Element element, double value) {
            elementSpellEfficiency.put(element, value);
            return this;
        }

        public Builder regenerateDuringPostCastLockout(boolean value) {
            regenerateDuringPostCastLockout = value;
            return this;
        }

        public Builder regenerateDuringContinuousCasting(boolean value) {
            regenerateDuringContinuousCasting = value;
            return this;
        }

        public ManaContribution build() {
            return new ManaContribution(this);
        }
    }
}
