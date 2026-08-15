package com.mahghuuuls.jawms.api;

import electroblob.wizardry.constants.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable server-only explanation of the latest accepted mana profile. */
public final class ManaProfileExplanation {

    private final long evaluatedWorldTick;
    private final long gameplayRulesRevision;
    private final int configuredBaseMaximumMana;
    private final double configuredBaseRegeneration;
    private final double configuredBaseLockoutSeconds;
    private final ManaProfileAggregation maximumManaAggregation;
    private final ManaProfileAggregation regenerationAggregation;
    private final ManaProfileAggregation lockoutAggregation;
    private final int finalMaximumMana;
    private final double finalRegeneration;
    private final double finalLockoutSeconds;
    private final long finalLockoutTicks;
    private final double globalSpellEfficiency;
    private final Map<Element, Double> elementSpellEfficiency;
    private final double globalSpellCostMultiplier;
    private final Map<Element, Double> elementSpellCostMultipliers;
    private final boolean regenerateDuringPostCastLockout;
    private final boolean regenerateDuringContinuousCasting;
    private final List<ManaContributionExplanation> contributions;

    public ManaProfileExplanation(
            long evaluatedWorldTick,
            long gameplayRulesRevision,
            int configuredBaseMaximumMana,
            double configuredBaseRegeneration,
            double configuredBaseLockoutSeconds,
            ManaProfileAggregation maximumManaAggregation,
            ManaProfileAggregation regenerationAggregation,
            ManaProfileAggregation lockoutAggregation,
            int finalMaximumMana,
            double finalRegeneration,
            double finalLockoutSeconds,
            long finalLockoutTicks,
            double globalSpellEfficiency,
            Map<Element, Double> elementSpellEfficiency,
            double globalSpellCostMultiplier,
            Map<Element, Double> elementSpellCostMultipliers,
            boolean regenerateDuringPostCastLockout,
            boolean regenerateDuringContinuousCasting,
            List<ManaContributionExplanation> contributions
    ) {
        if (evaluatedWorldTick < 0L || gameplayRulesRevision < 0L) {
            throw new IllegalArgumentException("Tick and rules revision must be non-negative.");
        }
        if (configuredBaseMaximumMana < 1 || finalMaximumMana < 1
                || finalLockoutTicks < 0L) {
            throw new IllegalArgumentException("Maximum mana and lockout bounds are invalid.");
        }
        requireNonNegativeFinite(configuredBaseRegeneration, "configuredBaseRegeneration");
        requireNonNegativeFinite(configuredBaseLockoutSeconds, "configuredBaseLockoutSeconds");
        requireNonNegativeFinite(finalRegeneration, "finalRegeneration");
        requireNonNegativeFinite(finalLockoutSeconds, "finalLockoutSeconds");
        requireNonNegativeFinite(globalSpellEfficiency, "globalSpellEfficiency");
        requirePositiveFinite(globalSpellCostMultiplier, "globalSpellCostMultiplier");
        this.evaluatedWorldTick = evaluatedWorldTick;
        this.gameplayRulesRevision = gameplayRulesRevision;
        this.configuredBaseMaximumMana = configuredBaseMaximumMana;
        this.configuredBaseRegeneration = configuredBaseRegeneration;
        this.configuredBaseLockoutSeconds = configuredBaseLockoutSeconds;
        this.maximumManaAggregation = Objects.requireNonNull(
                maximumManaAggregation, "maximumManaAggregation"
        );
        this.regenerationAggregation = Objects.requireNonNull(
                regenerationAggregation, "regenerationAggregation"
        );
        this.lockoutAggregation = Objects.requireNonNull(
                lockoutAggregation, "lockoutAggregation"
        );
        this.finalMaximumMana = finalMaximumMana;
        this.finalRegeneration = finalRegeneration;
        this.finalLockoutSeconds = finalLockoutSeconds;
        this.finalLockoutTicks = finalLockoutTicks;
        this.globalSpellEfficiency = globalSpellEfficiency;
        this.elementSpellEfficiency = immutableNonNegativeMap(
                elementSpellEfficiency, "elementSpellEfficiency"
        );
        this.globalSpellCostMultiplier = globalSpellCostMultiplier;
        this.elementSpellCostMultipliers = immutablePositiveMap(
                elementSpellCostMultipliers, "elementSpellCostMultipliers"
        );
        this.regenerateDuringPostCastLockout = regenerateDuringPostCastLockout;
        this.regenerateDuringContinuousCasting = regenerateDuringContinuousCasting;
        if (contributions == null || contributions.contains(null)) {
            throw new NullPointerException("contributions must not contain null");
        }
        this.contributions = Collections.unmodifiableList(new ArrayList<>(contributions));
    }

    public long getEvaluatedWorldTick() {
        return evaluatedWorldTick;
    }

    public long getGameplayRulesRevision() {
        return gameplayRulesRevision;
    }

    public int getConfiguredBaseMaximumMana() {
        return configuredBaseMaximumMana;
    }

    public double getConfiguredBaseRegeneration() {
        return configuredBaseRegeneration;
    }

    public double getConfiguredBaseLockoutSeconds() {
        return configuredBaseLockoutSeconds;
    }

    public ManaProfileAggregation getMaximumManaAggregation() {
        return maximumManaAggregation;
    }

    public ManaProfileAggregation getRegenerationAggregation() {
        return regenerationAggregation;
    }

    public ManaProfileAggregation getLockoutAggregation() {
        return lockoutAggregation;
    }

    public int getFinalMaximumMana() {
        return finalMaximumMana;
    }

    public double getFinalRegeneration() {
        return finalRegeneration;
    }

    public double getFinalLockoutSeconds() {
        return finalLockoutSeconds;
    }

    public long getFinalLockoutTicks() {
        return finalLockoutTicks;
    }

    public double getGlobalSpellEfficiency() {
        return globalSpellEfficiency;
    }

    /** Returns element-only efficiency points; add the global value for applicability. */
    public Map<Element, Double> getElementSpellEfficiency() {
        return elementSpellEfficiency;
    }

    public double getGlobalSpellCostMultiplier() {
        return globalSpellCostMultiplier;
    }

    /** Returns final global-times-element multipliers for every Wizardry element. */
    public Map<Element, Double> getElementSpellCostMultipliers() {
        return elementSpellCostMultipliers;
    }

    public boolean canRegenerateDuringPostCastLockout() {
        return regenerateDuringPostCastLockout;
    }

    public boolean canRegenerateDuringContinuousCasting() {
        return regenerateDuringContinuousCasting;
    }

    public List<ManaContributionExplanation> getContributions() {
        return contributions;
    }

    private static Map<Element, Double> immutableNonNegativeMap(
            Map<Element, Double> source, String name
    ) {
        if (source == null) {
            throw new NullPointerException(name);
        }
        EnumMap<Element, Double> result = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Double> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new NullPointerException(name + " key");
            }
            requireNonNegativeFinite(entry.getValue(), name + " value");
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Element, Double> immutablePositiveMap(
            Map<Element, Double> source, String name
    ) {
        if (source == null) {
            throw new NullPointerException(name);
        }
        EnumMap<Element, Double> result = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Double> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new NullPointerException(name + " key");
            }
            requirePositiveFinite(entry.getValue(), name + " value");
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and positive.");
        }
    }
}
