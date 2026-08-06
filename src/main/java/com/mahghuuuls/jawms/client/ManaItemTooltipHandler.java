package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaTooltips;
import com.mahghuuuls.jawms.internal.modifier.ActiveStackCollector;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryPassiveContributionAdapter;
import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryKillManaService;
import net.minecraft.item.ItemArmor;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.util.WandHelper;
import electroblob.wizardry.Wizardry;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ManaItemTooltipHandler {

    private static final int WIZARDRY_TOOLTIP_WRAP_WIDTH = 140;

    private static final Set<String> OBSOLETE_WAND_MANA_ITEMS = new HashSet<>(Arrays.asList(
            "ebwizardry:storage_upgrade",
            "ebwizardry:siphon_upgrade",
            "ebwizardry:condenser_upgrade",
            "ebwizardry:melee_upgrade",
            "ebwizardry:ring_condensing",
            "ebwizardry:ring_siphoning",
            "ebwizardry:ring_extraction",
            "ebwizardry:ring_mana_return",
            "ebwizardry:charm_hunger_casting"
    ));

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        removeWandDurabilityLine(event);
        removeSupersededWizardryCostLines(event);
        addWizardryIntegrationPresentation(event);
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

    private static void addWizardryIntegrationPresentation(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem().getRegistryName() == null) {
            return;
        }
        String id = stack.getItem().getRegistryName().toString();
        WizardryIntegrationRules rules = ClientGameplayRules.getIntegrationRules();
        switch (id) {
            case "ebwizardry:storage_upgrade":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isStorageEnabled(),
                        "tooltip.jawms.storage_per_level",
                        compact(rules.getStorageManaIncreasePerLevel()));
                return;
            case "ebwizardry:condenser_upgrade":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isCondenserEnabled(),
                        "tooltip.jawms.condenser_per_level",
                        compact(rules.getCondenserRegenerationIncreasePerLevel()));
                return;
            case "ebwizardry:siphon_upgrade":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isSiphonEnabled(),
                        "tooltip.jawms.siphon_per_level",
                        rules.getSiphonManaPerLevel());
                return;
            case "ebwizardry:melee_upgrade":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isMeleeEnabled(),
                        "tooltip.jawms.melee_per_level",
                        compact(rules.getMeleeDamagePerLevel()),
                        rules.getMeleeManaPerLevel());
                return;
            case "ebwizardry:ring_condensing":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isCondensingEnabled(),
                        "tooltip.jawms.condensing",
                        compact(rules.getCondensingRegenerationPerRing()));
                return;
            case "ebwizardry:ring_siphoning":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isSiphoningEnabled(),
                        "tooltip.jawms.siphoning",
                        compact(rules.getSiphoningIncrease()));
                return;
            case "ebwizardry:ring_extraction":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isExtractionEnabled(),
                        "tooltip.jawms.extraction",
                        rules.getExtractionMana());
                return;
            case "ebwizardry:ring_mana_return":
                removeWizardryDescription(event, id);
                addEnabledOrInactive(event, rules.isPerfectionistEnabled(),
                        "tooltip.jawms.perfectionist",
                        compact(rules.getPerfectionistRefund()));
                return;
            case "ebwizardry:charm_hunger_casting":
                removeWizardryDescription(event, id);
                if (!rules.isDemonicSealEnabled()) {
                    addConfigInactive(event);
                    return;
                }
                event.getToolTip().add(TextFormatting.BLUE
                        + I18n.translateToLocalFormatted(
                                "tooltip.jawms.demonic_seal",
                                rules.getDemonicSealManaPerHunger(),
                                rules.getDemonicSealMinimumHunger()
                        ));
                if (rules.isDemonicSealContinuous()) {
                    event.getToolTip().add(TextFormatting.BLUE
                            + I18n.translateToLocal(
                                    "tooltip.jawms.demonic_seal_continuous"
                            ));
                }
                return;
            default:
                break;
        }
        if (stack.getItem() instanceof ItemWand) {
            addHeldUpgradeLines(event, stack, rules);
            return;
        }
        if (isObsoleteWandManaItem(id)) {
            addInactive(event);
        }
    }

    private static void addHeldUpgradeLines(
            ItemTooltipEvent event, ItemStack stack, WizardryIntegrationRules rules
    ) {
        int storage = Math.max(0, WandHelper.getUpgradeLevel(
                stack, WizardryItems.storage_upgrade
        ));
        if (storage > 0) {
            if (rules.isStorageEnabled()) {
                double value = WizardryPassiveContributionAdapter.storageMaximumIncrease(
                        storage, rules
                );
                event.getToolTip().add(TextFormatting.BLUE + I18n.translateToLocalFormatted(
                        "tooltip.jawms.storage_installed", compact(value), storage
                ));
            } else {
                addConfigInactive(event);
            }
        }
        int condenser = Math.max(0, WandHelper.getUpgradeLevel(
                stack, WizardryItems.condenser_upgrade
        ));
        if (condenser > 0) {
            if (rules.isCondenserEnabled()) {
                event.getToolTip().add(TextFormatting.BLUE + I18n.translateToLocalFormatted(
                        "tooltip.jawms.condenser_installed",
                        compact(WizardryPassiveContributionAdapter.condenserRegenerationIncrease(
                                condenser, rules
                        )),
                        condenser
                ));
            } else {
                addConfigInactive(event);
            }
        }
        int siphon = Math.max(0, WandHelper.getUpgradeLevel(
                stack, WizardryItems.siphon_upgrade
        ));
        if (siphon > 0) {
            if (rules.isSiphonEnabled()) {
                event.getToolTip().add(TextFormatting.BLUE + I18n.translateToLocalFormatted(
                        "tooltip.jawms.siphon_installed",
                        WizardryKillManaService.calculateSiphonRestoration(
                                siphon, rules.getSiphonManaPerLevel(), false, 0.0D
                        ),
                        siphon
                ));
            } else {
                addConfigInactive(event);
            }
        }
        int melee = Math.max(0, WandHelper.getUpgradeLevel(
                stack, WizardryItems.melee_upgrade
        ));
        if (melee > 0) {
            if (rules.isMeleeEnabled()) {
                event.getToolTip().add(TextFormatting.BLUE + I18n.translateToLocalFormatted(
                        "tooltip.jawms.melee_installed",
                        compact(melee * rules.getMeleeDamagePerLevel()),
                        melee * rules.getMeleeManaPerLevel(),
                        melee
                ));
            } else {
                addConfigInactive(event);
            }
        }
    }

    private static void addEnabledOrInactive(
            ItemTooltipEvent event, boolean enabled, String key, Object... values
    ) {
        if (!enabled) {
            addConfigInactive(event);
            return;
        }
        event.getToolTip().add(TextFormatting.BLUE
                + I18n.translateToLocalFormatted(key, values));
    }

    private static void addInactive(ItemTooltipEvent event) {
        String inactive = TextFormatting.RED
                + I18n.translateToLocal("tooltip.jawms.inactive_wand_mana");
        if (!event.getToolTip().contains(inactive)) {
            event.getToolTip().add(inactive);
        }
    }

    private static void addConfigInactive(ItemTooltipEvent event) {
        String inactive = TextFormatting.RED
                + I18n.translateToLocal("tooltip.jawms.disabled_in_config");
        if (!event.getToolTip().contains(inactive)) {
            event.getToolTip().add(inactive);
        }
    }

    private static void removeWizardryDescription(ItemTooltipEvent event, String id) {
        String key = "item." + id + ".desc";
        java.util.List<String> lines = Minecraft.getMinecraft().fontRenderer
                .listFormattedStringToWidth(
                        Wizardry.proxy.translate(
                                key, new Style().setColor(TextFormatting.GRAY)
                        ),
                        WIZARDRY_TOOLTIP_WRAP_WIDTH
                );
        event.getToolTip().removeAll(lines);
    }

    static boolean isObsoleteWandManaItem(String registryName) {
        return OBSOLETE_WAND_MANA_ITEMS.contains(registryName);
    }

    private static void removeWandDurabilityLine(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        removeWandDurabilityLine(
                event.getToolTip(),
                stack.getItem() instanceof ItemWand,
                stack.getMaxDamage() - stack.getItemDamage(),
                stack.getMaxDamage()
        );
    }

    static void removeWandDurabilityLine(
            java.util.List<String> tooltip,
            boolean wizardryWand,
            int remainingDurability,
            int maximumDurability
    ) {
        if (!wizardryWand) {
            return;
        }
        tooltip.remove(I18n.translateToLocalFormatted(
                "item.durability", remainingDurability, maximumDurability
        ));
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

    static String compact(double value) {
        return value == Math.rint(value) && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE
                ? Long.toString((long) value) : Double.toString(value);
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
