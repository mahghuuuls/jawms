package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameplayRules {

    public static final int DEFAULT_BASE_MAXIMUM = 50;
    public static final double DEFAULT_BASE_REGENERATION = 2.0D;
    public static final double DEFAULT_LOCKOUT_SECONDS = 2.0D;
    public static final String DEFAULT_COST_FORMULA = "min(original_cost, tier * 25)";
    public static final int DEFAULT_SMALL_FLASK = 20;
    public static final int DEFAULT_MEDIUM_FLASK = 50;
    public static final int DEFAULT_LARGE_FLASK = 100;

    private final long revision;
    private final int baseMaximum;
    private final double baseRegeneration;
    private final long regenerationLockoutTicks;
    private final String costFormula;
    private final int smallFlask;
    private final int mediumFlask;
    private final int largeFlask;
    private final Map<ResourceLocation, Double> spellCosts;
    private final List<ConfiguredItemRule> configuredItemRules;
    private final WizardryArmorProfile armorProfile;

    public GameplayRules(
            long revision,
            int baseMaximum,
            double baseRegeneration,
            long regenerationLockoutTicks,
            String costFormula,
            int smallFlask,
            int mediumFlask,
            int largeFlask,
            Map<ResourceLocation, Double> spellCosts,
            List<ConfiguredItemRule> configuredItemRules,
            WizardryArmorProfile armorProfile
    ) {
        if (revision < 0L) {
            throw new IllegalArgumentException("Revision must be non-negative.");
        }
        if (baseMaximum < 1) {
            throw new IllegalArgumentException("Base maximum must be positive.");
        }
        if (!Double.isFinite(baseRegeneration) || baseRegeneration < 0.0D) {
            throw new IllegalArgumentException("Base regeneration must be finite and non-negative.");
        }
        if (regenerationLockoutTicks < 0L) {
            throw new IllegalArgumentException("Lockout ticks must be non-negative.");
        }
        if (smallFlask < 0 || mediumFlask < 0 || largeFlask < 0) {
            throw new IllegalArgumentException("Flask values must be non-negative.");
        }
        this.revision = revision;
        this.baseMaximum = baseMaximum;
        this.baseRegeneration = baseRegeneration;
        this.regenerationLockoutTicks = regenerationLockoutTicks;
        this.costFormula = costFormula;
        this.smallFlask = smallFlask;
        this.mediumFlask = mediumFlask;
        this.largeFlask = largeFlask;
        this.spellCosts = Collections.unmodifiableMap(new LinkedHashMap<>(spellCosts));
        this.configuredItemRules = Collections.unmodifiableList(new ArrayList<>(configuredItemRules));
        this.armorProfile = armorProfile;
    }

    public static GameplayRules initialDefaults() {
        return new GameplayRules(
                0L,
                DEFAULT_BASE_MAXIMUM,
                DEFAULT_BASE_REGENERATION,
                secondsToTicks(DEFAULT_LOCKOUT_SECONDS),
                DEFAULT_COST_FORMULA,
                DEFAULT_SMALL_FLASK,
                DEFAULT_MEDIUM_FLASK,
                DEFAULT_LARGE_FLASK,
                Collections.emptyMap(),
                Collections.emptyList(),
                WizardryArmorProfile.DEFAULT
        );
    }

    public long getRevision() {
        return revision;
    }

    public int getBaseMaximum() {
        return baseMaximum;
    }

    public double getBaseRegeneration() {
        return baseRegeneration;
    }

    public long getRegenerationLockoutTicks() {
        return regenerationLockoutTicks;
    }

    public String getCostFormula() {
        return costFormula;
    }

    public int getSmallFlask() {
        return smallFlask;
    }

    public int getMediumFlask() {
        return mediumFlask;
    }

    public int getLargeFlask() {
        return largeFlask;
    }

    public Map<ResourceLocation, Double> getSpellCosts() {
        return spellCosts;
    }

    public double getSpellCost(ResourceLocation spellId, double originalCost) {
        Double configured = spellCosts.get(spellId);
        return configured == null ? originalCost : configured;
    }

    public List<ConfiguredItemRule> getConfiguredItemRules() {
        return configuredItemRules;
    }

    public WizardryArmorProfile getArmorProfile() {
        return armorProfile;
    }

    public static long secondsToTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0D) {
            throw new IllegalArgumentException("Lockout seconds must be finite and non-negative.");
        }
        if (seconds == 0.0D) {
            return 0L;
        }
        double ticks = Math.ceil(seconds * 20.0D);
        if (!Double.isFinite(ticks) || ticks >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) ticks;
    }
}
