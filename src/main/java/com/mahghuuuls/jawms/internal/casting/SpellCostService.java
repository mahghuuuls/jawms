package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.util.ResourceLocation;

public final class SpellCostService {

    private SpellCostService() {
    }

    public static int getFinalCost(Spell spell, SpellModifiers modifiers) {
        if (spell == null) {
            throw new NullPointerException("spell");
        }
        if (modifiers == null) {
            throw new NullPointerException("modifiers");
        }
        GameplayRules rules = GameplayRulesManager.current();
        ResourceLocation spellId = spell.getRegistryName();
        double baseCost = spellId == null
                ? spell.getCost()
                : rules.getSpellCost(spellId, spell.getCost());
        return roundFinalCost(baseCost, modifiers.get(SpellModifiers.COST));
    }

    public static int roundFinalCost(double baseCost, float multiplier) {
        if (!Double.isFinite(baseCost) || baseCost < 0.0D) {
            throw new IllegalArgumentException("Configured base spell cost must be finite and non-negative.");
        }
        if (baseCost == 0.0D || multiplier <= 0.0F) {
            return 0;
        }
        double contextualCost = baseCost * multiplier;
        if (!Double.isFinite(contextualCost) || contextualCost >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(contextualCost);
    }
}
