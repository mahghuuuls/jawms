package com.mahghuuuls.jawms.internal.modifier;

import electroblob.wizardry.constants.Element;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ResolvedManaProfile {

    private final int maximumMana;
    private final double regeneration;
    private final double lockoutSeconds;
    private final long lockoutTicks;
    private final double globalSpellEfficiency;
    private final Map<Element, Double> elementSpellEfficiency;
    private final boolean regenerateDuringPostCastLockout;
    private final boolean regenerateDuringContinuousCasting;

    public ResolvedManaProfile(
            int maximumMana,
            double regeneration,
            double lockoutSeconds,
            long lockoutTicks,
            double globalSpellEfficiency,
            Map<Element, Double> elementSpellEfficiency,
            boolean regenerateDuringPostCastLockout,
            boolean regenerateDuringContinuousCasting
    ) {
        this.maximumMana = maximumMana;
        this.regeneration = regeneration;
        this.lockoutSeconds = lockoutSeconds;
        this.lockoutTicks = lockoutTicks;
        this.globalSpellEfficiency = globalSpellEfficiency;
        EnumMap<Element, Double> efficiencies = new EnumMap<>(Element.class);
        efficiencies.putAll(elementSpellEfficiency);
        this.elementSpellEfficiency = Collections.unmodifiableMap(efficiencies);
        this.regenerateDuringPostCastLockout = regenerateDuringPostCastLockout;
        this.regenerateDuringContinuousCasting = regenerateDuringContinuousCasting;
    }

    public int getMaximumMana() {
        return maximumMana;
    }

    public double getRegeneration() {
        return regeneration;
    }

    public double getLockoutSeconds() {
        return lockoutSeconds;
    }

    public long getLockoutTicks() {
        return lockoutTicks;
    }

    public double getGlobalSpellEfficiency() {
        return globalSpellEfficiency;
    }

    public double getSpellEfficiency(Element element) {
        Double value = elementSpellEfficiency.get(element);
        return saturatedAdd(globalSpellEfficiency, value == null ? 0.0D : value);
    }

    public Map<Element, Double> getElementSpellEfficiency() {
        return elementSpellEfficiency;
    }

    public boolean canRegenerateDuringPostCastLockout() {
        return regenerateDuringPostCastLockout;
    }

    public boolean canRegenerateDuringContinuousCasting() {
        return regenerateDuringContinuousCasting;
    }

    private static double saturatedAdd(double left, double right) {
        double result = left + right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }
}
