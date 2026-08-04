package com.mahghuuuls.jawms.internal.integration.wizardry;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.event.ArtefactCheckEvent;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.List;

/** Converts approved live Wizardry state into ordinary internal resolver contributions. */
public final class WizardryPassiveContributionAdapter {

    private WizardryPassiveContributionAdapter() {
    }

    public static List<ManaContribution> collect(
            EntityPlayer player, WizardryIntegrationRules rules
    ) {
        if (player == null || rules == null) {
            throw new NullPointerException("player and rules must not be null");
        }
        List<ManaContribution> result = new ArrayList<>(3);
        ManaContribution held = heldWandContribution(player, rules);
        if (held != null) {
            result.add(held);
        }
        int condensingCount = rules.isCondensingEnabled()
                ? activeCondensingRingCount(player)
                : 0;
        if (condensingCount > 0) {
            result.add(condensingContribution(condensingCount, rules));
        }
        if (rules.isFontEnabled() && WizardryPotions.font_of_mana != null) {
            PotionEffect effect = player.getActivePotionEffect(WizardryPotions.font_of_mana);
            if (effect != null) {
                result.add(fontContribution(effect.getAmplifier(), rules));
            }
        }
        return result;
    }

    public static int aggregateHeldUpgrade(
            EntityPlayer player,
            Item upgrade,
            WizardryIntegrationRules.HeldWandAggregation aggregation
    ) {
        int main = upgradeLevel(player.getHeldItemMainhand(), upgrade);
        int off = upgradeLevel(player.getHeldItemOffhand(), upgrade);
        return aggregateLevels(main, off, aggregation);
    }

    private static ManaContribution heldWandContribution(
            EntityPlayer player, WizardryIntegrationRules rules
    ) {
        int storageLevel = rules.isStorageEnabled()
                ? aggregateHeldUpgrade(player, WizardryItems.storage_upgrade,
                rules.getHeldWandAggregation())
                : 0;
        int condenserLevel = rules.isCondenserEnabled()
                ? aggregateHeldUpgrade(player, WizardryItems.condenser_upgrade,
                rules.getHeldWandAggregation())
                : 0;
        return heldContribution(storageLevel, condenserLevel, rules);
    }

    static int aggregateLevels(
            int main, int off, WizardryIntegrationRules.HeldWandAggregation aggregation
    ) {
        main = Math.max(0, main);
        off = Math.max(0, off);
        if (aggregation == WizardryIntegrationRules.HeldWandAggregation.HIGHEST) {
            return Math.max(main, off);
        }
        long sum = (long) main + (long) off;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    static ManaContribution heldContribution(
            int storageLevel, int condenserLevel, WizardryIntegrationRules rules
    ) {
        storageLevel = Math.max(0, storageLevel);
        condenserLevel = Math.max(0, condenserLevel);
        if (storageLevel == 0 && condenserLevel == 0) {
            return null;
        }
        return ManaContribution.builder()
                .maximumManaIncrease(storageMaximumIncrease(storageLevel, rules))
                .regenerationIncrease(condenserRegenerationIncrease(condenserLevel, rules))
                .build();
    }

    public static double storageMaximumIncrease(
            int level, WizardryIntegrationRules rules
    ) {
        return saturatedMultiply(
                rules.getStorageManaIncreasePerLevel(), Math.max(0, level)
        );
    }

    public static double condenserRegenerationIncrease(
            int level, WizardryIntegrationRules rules
    ) {
        return saturatedMultiply(
                rules.getCondenserRegenerationIncreasePerLevel(), Math.max(0, level)
        );
    }

    public static int activeCondensingRingCount(EntityPlayer player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        if (!(WizardryItems.ring_condensing instanceof ItemArtefact)) {
            return 0;
        }
        ItemArtefact condensing = (ItemArtefact) WizardryItems.ring_condensing;
        if (!condensing.isEnabled()) {
            return 0;
        }
        ArtefactCheckEvent event = new ArtefactCheckEvent(player, condensing);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return 0;
        }
        int count = 0;
        for (ItemArtefact artefact : ItemArtefact.getActiveArtefacts(
                player, ItemArtefact.Type.RING
        )) {
            if (artefact == WizardryItems.ring_condensing) {
                count++;
            }
        }
        // Preserve ArtefactCheckEvent.ALLOW compatibility when no physical stack was selected.
        return event.getResult() == Event.Result.ALLOW ? Math.max(1, count) : count;
    }

    static ManaContribution condensingContribution(
            int activeRingCount, WizardryIntegrationRules rules
    ) {
        return ManaContribution.builder()
                .flatRegeneration(saturatedMultiply(
                        rules.getCondensingRegenerationPerRing(),
                        Math.max(0, activeRingCount)
                ))
                .build();
    }

    static ManaContribution fontContribution(
            int amplifier, WizardryIntegrationRules rules
    ) {
        long level = Math.max(1L, (long) amplifier + 1L);
        return ManaContribution.builder()
                .flatRegeneration(saturatedMultiply(
                        rules.getFontRegenerationPerLevel(), level
                ))
                .regenerateDuringPostCastLockout(rules.isFontLockoutPermission())
                .regenerateDuringContinuousCasting(rules.isFontContinuousPermission())
                .build();
    }

    private static int upgradeLevel(ItemStack stack, Item upgrade) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return 0;
        }
        return Math.max(0, WandHelper.getUpgradeLevel(stack, upgrade));
    }

    private static double saturatedMultiply(double value, long multiplier) {
        double product = value * multiplier;
        return Double.isFinite(product) ? product : Double.MAX_VALUE;
    }
}
