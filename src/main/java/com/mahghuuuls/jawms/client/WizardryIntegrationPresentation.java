package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

public final class WizardryIntegrationPresentation {

    private WizardryIntegrationPresentation() {
    }

    public static String appendSpellInformation(Spell spell, String original) {
        if (spell != Spells.font_of_mana) {
            return original;
        }
        WizardryIntegrationRules rules = ClientGameplayRules.getIntegrationRules();
        String replacement;
        if (!rules.isFontEnabled()) {
            replacement = TextFormatting.RED
                    + I18n.format(fontInformationKey(rules));
        } else {
            replacement = TextFormatting.BLUE + I18n.format(
                    "gui.jawms.spell_book.font_of_mana",
                    compact(rules.getFontRegenerationPerLevel()),
                    yesNo(rules.isFontLockoutPermission()),
                    yesNo(rules.isFontContinuousPermission())
            );
        }
        return original + "\n\n" + replacement;
    }

    static String fontInformationKey(WizardryIntegrationRules rules) {
        return rules.isFontEnabled()
                ? "gui.jawms.spell_book.font_of_mana"
                : "tooltip.jawms.disabled_in_config";
    }

    private static String yesNo(boolean value) {
        return I18n.format(value ? "gui.yes" : "gui.no");
    }

    static String compact(double value) {
        return value == Math.rint(value) && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE
                ? Long.toString((long) value) : Double.toString(value);
    }
}
