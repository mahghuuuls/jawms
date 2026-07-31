package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure formatter shared by automatic configured/default contribution tooltips. */
public final class ManaContributionTooltipFormatter {

    private ManaContributionTooltipFormatter() {
    }

    public static List<String> format(ManaContribution contribution) {
        List<String> lines = new ArrayList<>();
        for (TooltipLine line : formatLines(contribution)) {
            lines.add(line.getText());
        }
        return lines;
    }

    static List<TooltipLine> formatLines(ManaContribution contribution) {
        List<TooltipLine> lines = new ArrayList<>();
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
            lines.add(new TooltipLine("Mana regenerates during post-cast delay", false));
        }
        if (contribution.canRegenerateDuringContinuousCasting()) {
            lines.add(new TooltipLine("Mana regenerates during continuous casting", false));
        }
        return lines;
    }

    private static void addSigned(
            List<TooltipLine> lines,
            double value,
            String label,
            boolean positiveIsDetrimental
    ) {
        if (value != 0.0D) {
            lines.add(new TooltipLine(
                    (value > 0.0D ? "+" : "") + number(value) + " " + label,
                    positiveIsDetrimental ? value > 0.0D : value < 0.0D
            ));
        }
    }

    private static void addPercentage(
            List<TooltipLine> lines,
            double value,
            String label,
            boolean negativeSign,
            boolean detrimental
    ) {
        if (value != 0.0D) {
            lines.add(new TooltipLine(
                    (negativeSign ? "-" : "+") + number(value) + "% " + label,
                    detrimental
            ));
        }
    }

    private static void addPositive(List<TooltipLine> lines, double value, String label) {
        if (value != 0.0D) {
            lines.add(new TooltipLine("+" + number(value) + " " + label + " points", false));
        }
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

    static final class TooltipLine {

        private final String text;
        private final boolean detrimental;

        private TooltipLine(String text, boolean detrimental) {
            this.text = text;
            this.detrimental = detrimental;
        }

        String getText() {
            return text;
        }

        boolean isDetrimental() {
            return detrimental;
        }
    }
}
