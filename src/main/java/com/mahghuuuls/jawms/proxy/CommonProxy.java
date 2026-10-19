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

    public boolean allowWandPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.INetHandler handler,
            net.minecraft.network.Packet<?> packet) { return true; }
    public void wandInventoryWrite(net.minecraft.entity.player.InventoryPlayer inventory, int index,
            net.minecraft.item.ItemStack previous, net.minecraft.item.ItemStack installed) {}
    public void wandSelectionChanged(net.minecraft.entity.player.InventoryPlayer inventory) {}

    public void acceptParticipantUseCapability(com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.Capability message, Object handler) {}
    public void acceptParticipantUseClose(com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.Close message, Object handler) {}
    public boolean isParticipantInputBlocked() { return false; }
    public void participantInputStarted(net.minecraft.entity.player.EntityPlayer player, EnumHand hand) {}
    public boolean isParticipantFinishSuppressed(EnumHand hand, ResourceLocation spell) { return false; }
    public com.mahghuuuls.jawms.internal.casting.FinishContinuation.Capture beginParticipantFinish(net.minecraft.entity.player.EntityPlayer player, net.minecraft.item.ItemStack stack, EnumHand hand, Spell spell) { return com.mahghuuuls.jawms.internal.casting.FinishContinuation.Capture.LEGACY; }
    public void endParticipantFinish(boolean completed) {}
    public boolean deferParticipantFinishAction(Runnable action) { return false; }
    public boolean isParticipantFinishCaptureActive() { return false; }
    public void captureParticipantBaseCooldown(int base) {}
    public boolean deferParticipantCooldown(int fallback, SpellModifiers modifiers,
            java.util.function.BiConsumer<net.minecraft.item.ItemStack, Integer> operation) { return false; }
    public void observeParticipantUseArgument(net.minecraft.entity.player.EntityPlayer player, net.minecraft.item.ItemStack stack) {}

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
