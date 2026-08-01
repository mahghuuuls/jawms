package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import electroblob.wizardry.item.ItemWizardArmour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GameplayRules {

    public static final int DEFAULT_BASE_MAXIMUM = 50;
    public static final double DEFAULT_BASE_REGENERATION = 2.0D;
    public static final double DEFAULT_LOCKOUT_SECONDS = 2.0D;
    public static final double DEFAULT_EFFICIENCY_SCALE = 100.0D;
    public static final String DEFAULT_COST_FORMULA = "min(original_cost, tier * 25)";
    public static final int DEFAULT_SMALL_FLASK = 20;
    public static final int DEFAULT_MEDIUM_FLASK = 50;
    public static final int DEFAULT_LARGE_FLASK = 100;

    private final long revision;
    private final int baseMaximum;
    private final double baseRegeneration;
    private final double regenerationLockoutSeconds;
    private final long regenerationLockoutTicks;
    private final String costFormula;
    private final int smallFlask;
    private final int mediumFlask;
    private final int largeFlask;
    private final Map<ResourceLocation, Double> spellCosts;
    private final List<ConfiguredItemRule> configuredItemRules;
    private final WizardryArmorProfile armorProfile;
    private final Set<ResourceLocation> generatedWizardryArmorIds;
    private final Set<ResourceLocation> disabledProviderIds;
    private final double efficiencyScale;
    private final WizardryArmorEfficiencyProfile armorEfficiencyProfile;

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
        this(
                revision, baseMaximum, baseRegeneration, regenerationLockoutTicks / 20.0D,
                costFormula, smallFlask, mediumFlask, largeFlask, spellCosts,
                configuredItemRules, armorProfile, Collections.emptySet()
        );
    }

    public GameplayRules(
            long revision,
            int baseMaximum,
            double baseRegeneration,
            double regenerationLockoutSeconds,
            String costFormula,
            int smallFlask,
            int mediumFlask,
            int largeFlask,
            Map<ResourceLocation, Double> spellCosts,
            List<ConfiguredItemRule> configuredItemRules,
            WizardryArmorProfile armorProfile,
            Set<ResourceLocation> disabledProviderIds
    ) {
        this(
                revision, baseMaximum, baseRegeneration, regenerationLockoutSeconds,
                costFormula, smallFlask, mediumFlask, largeFlask, spellCosts,
                configuredItemRules, armorProfile, disabledProviderIds,
                DEFAULT_EFFICIENCY_SCALE, WizardryArmorEfficiencyProfile.DEFAULT
        );
    }

    public GameplayRules(
            long revision,
            int baseMaximum,
            double baseRegeneration,
            double regenerationLockoutSeconds,
            String costFormula,
            int smallFlask,
            int mediumFlask,
            int largeFlask,
            Map<ResourceLocation, Double> spellCosts,
            List<ConfiguredItemRule> configuredItemRules,
            WizardryArmorProfile armorProfile,
            Set<ResourceLocation> disabledProviderIds,
            double efficiencyScale,
            WizardryArmorEfficiencyProfile armorEfficiencyProfile
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
        if (!Double.isFinite(regenerationLockoutSeconds)
                || regenerationLockoutSeconds < 0.0D) {
            throw new IllegalArgumentException("Lockout seconds must be finite and non-negative.");
        }
        if (smallFlask < 0 || mediumFlask < 0 || largeFlask < 0) {
            throw new IllegalArgumentException("Flask values must be non-negative.");
        }
        if (!Double.isFinite(efficiencyScale) || efficiencyScale <= 0.0D) {
            throw new IllegalArgumentException("Efficiency scale must be finite and positive.");
        }
        this.revision = revision;
        this.baseMaximum = baseMaximum;
        this.baseRegeneration = baseRegeneration;
        this.regenerationLockoutSeconds = regenerationLockoutSeconds;
        this.regenerationLockoutTicks = secondsToTicks(regenerationLockoutSeconds);
        this.costFormula = costFormula;
        this.smallFlask = smallFlask;
        this.mediumFlask = mediumFlask;
        this.largeFlask = largeFlask;
        this.spellCosts = Collections.unmodifiableMap(new LinkedHashMap<>(spellCosts));
        this.configuredItemRules = Collections.unmodifiableList(new ArrayList<>(configuredItemRules));
        this.armorProfile = armorProfile;
        this.generatedWizardryArmorIds = Collections.unmodifiableSet(discoverWizardryArmorIds());
        this.disabledProviderIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(disabledProviderIds)
        );
        this.efficiencyScale = efficiencyScale;
        this.armorEfficiencyProfile = armorEfficiencyProfile;
    }

    public GameplayRules(
            long revision,
            int baseMaximum,
            double baseRegeneration,
            double regenerationLockoutSeconds,
            String costFormula,
            int smallFlask,
            int mediumFlask,
            int largeFlask,
            Map<ResourceLocation, Double> spellCosts,
            List<ConfiguredItemRule> configuredItemRules,
            WizardryArmorProfile armorProfile
    ) {
        this(
                revision, baseMaximum, baseRegeneration, regenerationLockoutSeconds,
                costFormula, smallFlask, mediumFlask, largeFlask, spellCosts,
                configuredItemRules, armorProfile, Collections.emptySet()
        );
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

    public double getRegenerationLockoutSeconds() {
        return regenerationLockoutSeconds;
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

    public ConfiguredItemRule findConfiguredItemRule(ItemStack stack) {
        ConfiguredItemRule wildcard = null;
        for (ConfiguredItemRule rule : configuredItemRules) {
            if (!rule.matches(stack)) {
                continue;
            }
            if (rule.getMetadata() == stack.getMetadata()) {
                return rule;
            }
            wildcard = rule;
        }
        return wildcard;
    }

    public WizardryArmorProfile getArmorProfile() {
        return armorProfile;
    }

    public Set<ResourceLocation> getGeneratedWizardryArmorIds() {
        return generatedWizardryArmorIds;
    }

    public Set<ResourceLocation> getDisabledProviderIds() {
        return disabledProviderIds;
    }

    public boolean isProviderDisabled(ResourceLocation providerId) {
        return disabledProviderIds.contains(providerId);
    }

    public double getEfficiencyScale() {
        return efficiencyScale;
    }

    public WizardryArmorEfficiencyProfile getArmorEfficiencyProfile() {
        return armorEfficiencyProfile;
    }

    public boolean hasGeneratedWizardryArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem().getRegistryName() != null
                && generatedWizardryArmorIds.contains(stack.getItem().getRegistryName());
    }

    private static Set<ResourceLocation> discoverWizardryArmorIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Item item : Item.REGISTRY) {
            ResourceLocation id = item.getRegistryName();
            if (item instanceof ItemWizardArmour && id != null
                    && "ebwizardry".equals(id.getNamespace())) {
                ids.add(id);
            }
        }
        return ids;
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
