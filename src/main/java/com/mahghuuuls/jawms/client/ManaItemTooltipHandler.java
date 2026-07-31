package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.modifier.ActiveStackCollector;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ManaItemTooltipHandler {

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        ManaContribution contribution = contributionFor(event.getItemStack());
        if (contribution == null) {
            return;
        }
        java.util.List<ManaContributionTooltipFormatter.TooltipLine> lines =
                ManaContributionTooltipFormatter.formatLines(contribution);
        if (lines.isEmpty()) {
            return;
        }
        for (ManaContributionTooltipFormatter.TooltipLine line : lines) {
            event.getToolTip().add(
                    (line.isDetrimental() ? TextFormatting.RED : TextFormatting.BLUE)
                            + line.getText()
            );
        }
    }

    static ManaContribution contributionFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ConfiguredItemRule configured = configuredRuleFor(stack);
        if (configured != null) {
            return configured.getContribution();
        }
        if (ClientGameplayRules.isGeneratedWizardryArmor(stack)
                && stack.getItem() instanceof ItemArmor) {
            return ActiveStackCollector.generatedWizardryArmor(
                    ((ItemArmor) stack.getItem()).armorType,
                    ClientGameplayRules.getArmorProfile()
            );
        }
        return null;
    }

    private static ConfiguredItemRule configuredRuleFor(ItemStack stack) {
        ConfiguredItemRule exact = null;
        ConfiguredItemRule wildcard = null;
        for (ConfiguredItemRule rule : ClientGameplayRules.getItemRules()) {
            if (rule.matches(stack)) {
                if (rule.getMetadata() == ConfiguredItemRule.WILDCARD_METADATA) {
                    wildcard = rule;
                } else {
                    exact = rule;
                }
            }
        }
        if (exact != null || wildcard != null) {
            return exact != null ? exact : wildcard;
        }
        return null;
    }
}
