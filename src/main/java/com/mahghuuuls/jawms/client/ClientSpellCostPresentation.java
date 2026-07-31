package com.mahghuuuls.jawms.client;

import electroblob.wizardry.spell.Spell;

import java.math.BigDecimal;

public final class ClientSpellCostPresentation {

    private ClientSpellCostPresentation() {
    }

    public static double getConfiguredBaseCost(Spell spell) {
        if (spell == null) {
            throw new NullPointerException("spell");
        }
        if (ClientGameplayRules.getRevision() < 0L) {
            return spell.getCost();
        }
        return ClientGameplayRules.getSpellCost(
                spell.getRegistryName(),
                spell.getCost()
        );
    }

    public static String formatBaseCost(double cost) {
        if (!Double.isFinite(cost) || cost < 0.0D) {
            throw new IllegalArgumentException(
                    "Displayed base mana cost must be finite and non-negative."
            );
        }
        return BigDecimal.valueOf(cost).stripTrailingZeros().toPlainString();
    }
}
