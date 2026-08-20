package com.mahghuuuls.jawms.proxy;

import com.mahghuuuls.jawms.client.ClientManaState;
import com.mahghuuuls.jawms.client.ClientManaViewAdapter;
import com.mahghuuuls.jawms.client.ClientGameplayRules;
import com.mahghuuuls.jawms.client.ClientContinuousCastGuard;
import com.mahghuuuls.jawms.client.ClientHudConfigManager;
import com.mahghuuuls.jawms.client.ManaHudRenderer;
import com.mahghuuuls.jawms.client.ManaItemTooltipHandler;
import com.mahghuuuls.jawms.internal.casting.SpellCostService;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import com.mahghuuuls.jawms.internal.clientbridge.ClientManaViewBridge;
import com.mahghuuuls.jawms.internal.clientbridge.ClientConfiguredFlaskViewBridge;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import electroblob.wizardry.constants.Element;
import java.util.Map;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(File configurationDirectory) {
        ClientHudConfigManager.configure(configurationDirectory);
        ClientManaViewBridge.installClientView(new ClientManaViewAdapter());
        ClientConfiguredFlaskViewBridge.installClientView(
                ClientGameplayRules::getConfiguredFlaskSnapshot
        );
        MinecraftForge.EVENT_BUS.register(new ManaHudRenderer());
        MinecraftForge.EVENT_BUS.register(new ManaItemTooltipHandler());
        FMLCommonHandler.instance().bus().register(new ClientManaState());
        FMLCommonHandler.instance().bus().register(new ClientGameplayRules());
        FMLCommonHandler.instance().bus().register(new ClientContinuousCastGuard());
        FMLCommonHandler.instance().bus().register(new ClientHudConfigManager());
    }

    @Override
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
        ClientManaState.accept(currentMana, maximumMana, effectiveRegeneration, revision,
                globalSpellEfficiency, elementSpellEfficiency,
                globalSpellCostMultiplier, elementSpellCostMultiplier);
    }

    @Override
    public void acceptGameplayRules(GameplayRulesMessage message) {
        ClientGameplayRules.accept(message);
    }

    @Override
    public int getClientAvailableMana(int fallback) {
        return ClientManaState.isAvailable()
                ? ClientManaState.getCurrentMana()
                : fallback;
    }

    @Override
    public int getClientMissingMana() {
        return ClientManaState.isAvailable()
                ? Math.max(0, ClientManaState.getMaximumMana() - ClientManaState.getCurrentMana())
                : 0;
    }

    @Override
    public WizardryIntegrationRules getClientWizardryIntegrationRules() {
        return ClientGameplayRules.getIntegrationRules();
    }

    @Override
    public boolean isClientContinuousCastBlocked(Spell spell, EnumHand hand) {
        return spell.isContinuous
                && ClientContinuousCastGuard.isBlocked(
                        hand,
                        spell.getRegistryName()
                );
    }

    @Override
    public boolean isClientContinuousFinishSuppressed(Spell spell, EnumHand hand) {
        return spell.isContinuous
                && ClientContinuousCastGuard.isFinishSuppressed(
                        hand,
                        spell.getRegistryName()
                );
    }

    @Override
    public void blockClientContinuousCast(
            Spell spell,
            EnumHand hand,
            boolean suppressFinish
    ) {
        if (spell.isContinuous) {
            ClientContinuousCastGuard.block(
                    hand,
                    spell.getRegistryName(),
                    suppressFinish
            );
        }
    }

    @Override
    public int getClientFinalSpellCost(
            Spell spell,
            SpellModifiers modifiers,
            int fallback
    ) {
        if (ClientGameplayRules.getRevision() < 0L) {
            return fallback;
        }
        double baseCost = ClientGameplayRules.getSpellCost(
                spell.getRegistryName(),
                (double) spell.getCost()
        );
        return SpellCostService.roundFinalCost(
                baseCost,
                modifiers.get(SpellModifiers.COST),
                ClientManaState.getSpellCostMultiplier(spell.getElement()),
                ClientManaState.getSpellEfficiency(spell.getElement()),
                ClientGameplayRules.getEfficiencyScale()
        );
    }

    @Override
    public void stopContinuousCastVisual(
            EnumHand hand,
            ResourceLocation spellId,
            boolean resetImmediately
    ) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        CastCostContextManager.clear(player, hand);
        CastPaymentContextManager.clear(player, hand);
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemWand)) {
            return;
        }
        Spell spell = WandHelper.getCurrentSpell(stack);
        if (spell.isContinuous && spellId.equals(spell.getRegistryName())) {
            ClientContinuousCastGuard.block(hand, spellId, resetImmediately);
            if (resetImmediately) {
                player.resetActiveHand();
            }
        }
    }
}
