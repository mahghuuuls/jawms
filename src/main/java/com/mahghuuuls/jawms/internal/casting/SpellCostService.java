package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

public final class SpellCostService {

    private static final AtomicLong NEXT_FACTOR_WARNING = new AtomicLong();

    private SpellCostService() {
    }

    public static int getFinalCost(Spell spell, SpellModifiers modifiers) {
        return getFinalCost(null, spell, modifiers);
    }

    public static int getFinalCost(EntityPlayer player, Spell spell, SpellModifiers modifiers) {
        if (spell == null) {
            throw new NullPointerException("spell");
        }
        if (modifiers == null) {
            throw new NullPointerException("modifiers");
        }
        GameplayRules rules = GameplayRulesManager.current();
        ResourceLocation spellId = spell.getRegistryName();
        double baseCost = getConfiguredBaseCost(rules, spell);
        float factor = modifiers.get(SpellModifiers.COST);
        if ((!Float.isFinite(factor) || factor <= 0.0F) && player != null) {
            warnInvalidFactor(player, spellId, factor);
        }
        ManaService mana = player == null ? null : new ManaService();
        double points = mana == null ? 0.0D
                : mana.getApplicableSpellEfficiency(player, spell);
        double profileMultiplier = mana == null ? 1.0D
                : mana.getApplicableSpellCostMultiplier(player, spell);
        return roundFinalCost(
                baseCost, factor, profileMultiplier, points, rules.getEfficiencyScale()
        );
    }

    public static double getConfiguredBaseCost(GameplayRules rules, Spell spell) {
        ResourceLocation spellId = spell.getRegistryName();
        return spellId == null ? spell.getCost() : rules.getSpellCost(spellId, spell.getCost());
    }

    public static double retainedContextualFactor(float factor) {
        return Float.isFinite(factor) && factor > 0.0F ? factor : 1.0D;
    }

    public static int roundFinalCost(double baseCost, float multiplier) {
        return roundFinalCost(baseCost, multiplier, 0.0D, GameplayRules.DEFAULT_EFFICIENCY_SCALE);
    }

    public static int roundFinalCost(
            double baseCost, float contextualFactor, double efficiencyPoints, double scale
    ) {
        return roundFinalCost(baseCost, contextualFactor, 1.0D, efficiencyPoints, scale);
    }

    public static int roundFinalCost(
            double baseCost,
            float contextualFactor,
            double profileMultiplier,
            double efficiencyPoints,
            double scale
    ) {
        if (!Double.isFinite(baseCost) || baseCost < 0.0D) {
            throw new IllegalArgumentException("Configured base spell cost must be finite and non-negative.");
        }
        if (!Double.isFinite(efficiencyPoints) || efficiencyPoints < 0.0D) {
            throw new IllegalArgumentException("Efficiency points must be finite and non-negative.");
        }
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Efficiency scale must be finite and positive.");
        }
        if (!Double.isFinite(profileMultiplier) || profileMultiplier <= 0.0D) {
            throw new IllegalArgumentException("Spell-cost multiplier must be finite and positive.");
        }
        if (baseCost == 0.0D) {
            return 0;
        }
        BigDecimal denominator = decimal(scale).add(decimal(efficiencyPoints));
        BigDecimal numerator = decimal(baseCost)
                .multiply(decimal(retainedContextualFactor(contextualFactor)))
                .multiply(decimal(profileMultiplier))
                .multiply(decimal(scale));
        BigDecimal maximumCost = denominator.multiply(
                BigDecimal.valueOf(Integer.MAX_VALUE)
        );
        if (numerator.compareTo(maximumCost) >= 0) {
            return Integer.MAX_VALUE;
        }
        BigDecimal[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        int rounded = quotientAndRemainder[0].intValueExact();
        if (quotientAndRemainder[1].signum() > 0) {
            rounded++;
        }
        return Math.max(1, rounded);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static void warnInvalidFactor(EntityPlayer player, ResourceLocation spellId, float factor) {
        long now = System.currentTimeMillis();
        long next = NEXT_FACTOR_WARNING.get();
        if (now >= next && NEXT_FACTOR_WARNING.compareAndSet(next, now + 30_000L)) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "Ignored invalid nonpositive Wizardry COST factor {} for player {} and spell {}; using 1.",
                    factor, player.getUniqueID(), spellId
            );
        }
    }
}
