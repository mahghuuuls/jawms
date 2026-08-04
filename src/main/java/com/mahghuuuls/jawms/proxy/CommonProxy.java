package com.mahghuuuls.jawms.proxy;

import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import electroblob.wizardry.constants.Element;
import java.util.Map;

public class CommonProxy {

    public void preInit(File configurationDirectory) {
        // Common initialization is performed without referencing client classes.
    }

    public void acceptManaState(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision,
            double globalSpellEfficiency,
            Map<Element, Double> elementSpellEfficiency,
            double globalSpellCostMultiplier,
            Map<Element, Double> elementSpellCostMultiplier
    ) {
        // Server proxy intentionally ignores client-bound presentation state.
    }

    public void acceptGameplayRules(GameplayRulesMessage message) {
        // Server proxy intentionally ignores client-bound presentation rules.
    }

    public int getClientAvailableMana(int fallback) {
        return fallback;
    }

    public int getClientMissingMana() {
        return 0;
    }

    public WizardryIntegrationRules getClientWizardryIntegrationRules() {
        return null;
    }

    public boolean isClientContinuousCastBlocked(
            Spell spell,
            EnumHand hand
    ) {
        return false;
    }

    public boolean isClientContinuousFinishSuppressed(Spell spell, EnumHand hand) {
        return false;
    }

    public void blockClientContinuousCast(
            Spell spell,
            EnumHand hand,
            boolean suppressFinish
    ) {
        // Server proxy intentionally ignores client prediction state.
    }

    public int getClientFinalSpellCost(
            Spell spell,
            SpellModifiers modifiers,
            int fallback
    ) {
        return fallback;
    }

    public void stopContinuousCastVisual(
            EnumHand hand,
            ResourceLocation spellId,
            boolean resetImmediately
    ) {
        // Server proxy intentionally ignores client-bound visual state.
    }
}
