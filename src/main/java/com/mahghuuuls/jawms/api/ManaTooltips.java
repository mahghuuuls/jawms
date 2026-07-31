package com.mahghuuuls.jawms.api;

import electroblob.wizardry.constants.Element;
import net.minecraft.util.text.TextFormatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optional server-safe formatter for addon-owned contribution tooltips. */
public final class ManaTooltips {

    private ManaTooltips() {
    }

    public static List<String> format(ManaContribution contribution) {
        if (contribution == null) {
            throw new NullPointerException("contribution");
        }
        List<String> lines = new ArrayList<>();
        addSigned(lines, contribution.getFlatMaximumMana(), "Mana", false);
        addPercentage(lines, contribution.getMaximumManaIncrease(), "Mana", false, false);
        addPercentage(lines, contribution.getMaximumManaReduction(), "Mana", true, true);
        addSigned(lines, contribution.getFlatRegeneration(), "Mana regen", false);
        addPercentage(lines, contribution.getRegenerationIncrease(), "Mana regen", false, false);
        addPercentage(lines, contribution.getRegenerationReduction(), "Mana regen", true, true);
        addSigned(lines, contribution.getFlatLockoutSeconds(), "Mana regen delay", true);
        addPercentage(lines, contribution.getLockoutIncrease(), "Mana regen delay", false, true);
        addPercentage(lines, contribution.getLockoutReduction(), "Mana regen delay", true, false);
        addPositive(lines, contribution.getGlobalSpellEfficiency(), "Spell efficiency");
        for (Map.Entry<Element, Double> entry
                : contribution.getElementSpellEfficiency().entrySet()) {
            addPositive(
                    lines,
                    entry.getValue(),
                    titleCase(entry.getKey().name()) + " spell efficiency"
            );
        }
        if (contribution.canRegenerateDuringPostCastLockout()) {
            add(lines, "Mana regenerates during post-cast delay", false);
        }
        if (contribution.canRegenerateDuringContinuousCasting()) {
            add(lines, "Mana regenerates during continuous casting", false);
        }
        return Collections.unmodifiableList(lines);
    }

    private static void addSigned(
            List<String> lines,
            double value,
            String label,
            boolean positiveIsDetrimental
    ) {
        if (value != 0.0D) {
            add(
                    lines,
                    (value > 0.0D ? "+" : "") + number(value) + " " + label,
                    positiveIsDetrimental ? value > 0.0D : value < 0.0D
            );
        }
    }

    private static void addPercentage(
            List<String> lines,
            double value,
            String label,
            boolean negativeSign,
            boolean detrimental
    ) {
        if (value != 0.0D) {
            add(
                    lines,
                    (negativeSign ? "-" : "+") + number(value) + "% " + label,
                    detrimental
            );
        }
    }

    private static void addPositive(List<String> lines, double value, String label) {
        if (value != 0.0D) {
            add(lines, "+" + number(value) + " " + label + " points", false);
        }
    }

    private static void add(List<String> lines, String text, boolean detrimental) {
        lines.add((detrimental ? TextFormatting.RED : TextFormatting.BLUE) + text);
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String number(double value) {
        DecimalFormat format = new DecimalFormat(
                "0.##",
                DecimalFormatSymbols.getInstance(Locale.ROOT)
        );
        return format.format(value);
    }
}
