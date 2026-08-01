package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaTooltips;
import com.mahghuuuls.jawms.internal.modifier.ActiveStackCollector;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import net.minecraft.item.ItemArmor;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.Wizardry;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ManaItemTooltipHandler {

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        removeSupersededWizardryCostLines(event);
        ManaContribution contribution = contributionFor(event.getItemStack());
        if (contribution == null) {
            return;
        }
        event.getToolTip().addAll(ManaTooltips.format(contribution));
        if (event.getItemStack().getItem() instanceof ItemWizardArmour) {
            ItemWizardArmour armor = (ItemWizardArmour) event.getItemStack().getItem();
            if (armor.armourClass == ItemWizardArmour.ArmourClass.SAGE
                    && ClientGameplayRules.getArmorEfficiencyProfile().isEnabled()) {
                String heading = Wizardry.proxy.translate(
                        "item." + Wizardry.MODID + ":wizard_armour.full_set",
                        new Style().setColor(TextFormatting.AQUA)
                );
                String replacement = TextFormatting.AQUA + "+"
                        + compact(ClientGameplayRules.getArmorEfficiencyProfile()
                                .getSageNonmatchingFullSet())
                        + " nonmatching spell efficiency";
                insertAfter(event.getToolTip(), heading, replacement);
            }
        }
    }

    private static void removeSupersededWizardryCostLines(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof ItemWizardArmour)) {
            return;
        }
        ItemWizardArmour armor = (ItemWizardArmour) event.getItemStack().getItem();
        if (armor.element != null) {
            removeTranslatedPercentage(
                    event, "wizard_armour.element_cost_reduction",
                    TextFormatting.DARK_GRAY, armor.element.getDisplayName()
            );
        }
        if (armor.armourClass == ItemWizardArmour.ArmourClass.SAGE) {
            removeTranslatedPercentage(
                    event, "sage_armour.full_set_bonus", TextFormatting.AQUA
            );
        }
    }

    private static void removeTranslatedPercentage(
            ItemTooltipEvent event, String suffix, TextFormatting color, Object... trailing
    ) {
        for (int percentage = 0; percentage <= 100; percentage++) {
            Object[] arguments = new Object[trailing.length + 1];
            arguments[0] = percentage;
            System.arraycopy(trailing, 0, arguments, 1, trailing.length);
            if (event.getToolTip().remove(Wizardry.proxy.translate(
                    "item." + Wizardry.MODID + ":" + suffix,
                    new Style().setColor(color), arguments
            ))) {
                return;
            }
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
            ManaContribution mana = ActiveStackCollector.generatedWizardryArmor(
                    ((ItemArmor) stack.getItem()).armorType,
                    ClientGameplayRules.getArmorProfile()
            );
            if (!(stack.getItem() instanceof ItemWizardArmour)
                    || !ClientGameplayRules.getArmorEfficiencyProfile().isEnabled()) {
                return mana;
            }
            ItemWizardArmour armor = (ItemWizardArmour) stack.getItem();
            ManaContribution.Builder builder = ManaContribution.builder();
            if (mana != null) {
                builder.flatMaximumMana(mana.getFlatMaximumMana())
                        .flatRegeneration(mana.getFlatRegeneration());
            }
            double points = ClientGameplayRules.getArmorEfficiencyProfile()
                    .getPerPiece(armor.armourClass);
            builder.elementSpellEfficiency(
                    armor.element == null
                            ? electroblob.wizardry.constants.Element.MAGIC
                            : armor.element,
                    points
            );
            return builder.build();
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

    private static String compact(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    static void insertAfter(java.util.List<String> tooltip, String heading, String replacement) {
        int headingIndex = tooltip.indexOf(heading);
        if (headingIndex >= 0) {
            tooltip.add(headingIndex + 1, replacement);
        } else {
            tooltip.add(replacement);
        }
    }
}
