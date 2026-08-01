package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaContributionTooltipFormatterTest {

    @Test
    void labelsIncreasesReductionsEfficiencyAndPermissionsTruthfully() {
        ManaContribution contribution = ManaContribution.builder()
                .flatMaximumMana(15)
                .maximumManaIncrease(25)
                .maximumManaReduction(50)
                .flatRegeneration(0.5)
                .flatLockoutSeconds(-1)
                .lockoutReduction(20)
                .globalSpellEfficiency(5)
                .elementSpellEfficiency(Element.FIRE, 10)
                .globalSpellCostMultiplier(0.5D)
                .elementSpellCostMultiplier(Element.FIRE, 1.5D)
                .regenerateDuringPostCastLockout(true)
                .build();

        List<String> lines = ManaContributionTooltipFormatter.format(contribution);
        assertTrue(lines.contains("+15 Mana"));
        assertTrue(lines.contains("+25% Mana"));
        assertTrue(lines.contains("-50% Mana"));
        assertTrue(lines.contains("+0.5 Mana regen"));
        assertTrue(lines.contains("-1 Mana regen delay"));
        assertTrue(lines.contains("-20% Mana regen delay"));
        assertTrue(lines.contains("+5 Spell efficiency"));
        assertTrue(lines.contains("+10 Fire spell efficiency"));
        assertTrue(lines.contains("-50% Mana cost"));
        assertTrue(lines.contains("+50% Fire spell mana cost"));
        assertTrue(lines.contains("Mana regenerates during post-cast delay"));
    }

    @Test
    void colorsChangesByGameplayBenefitRatherThanRawSign() {
        ManaContribution contribution = ManaContribution.builder()
                .flatMaximumMana(-10)
                .maximumManaIncrease(10)
                .regenerationReduction(25)
                .flatLockoutSeconds(1)
                .lockoutReduction(50)
                .globalSpellCostMultiplier(1.5D)
                .elementSpellCostMultiplier(Element.FIRE, 0.5D)
                .build();

        java.util.List<ManaContributionTooltipFormatter.TooltipLine> lines =
                ManaContributionTooltipFormatter.formatLines(contribution);
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("-10 Mana")
                && line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("+10% Mana")
                && !line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("-25% Mana regen")
                && line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("+1 Mana regen delay")
                && line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("-50% Mana regen delay")
                && !line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("+50% Mana cost")
                && line.isDetrimental()));
        assertTrue(lines.stream().anyMatch(line -> line.getText().equals("-50% Fire spell mana cost")
                && !line.isDetrimental()));
    }

    @Test
    void sageReplacementIsInsertedImmediatelyAfterFullSetHeading() {
        List<String> tooltip = new ArrayList<>(java.util.Arrays.asList(
                "Sage Hat", "Extra enchantability", "Full set", "When on head", "+15 Mana"
        ));

        ManaItemTooltipHandler.insertAfter(
                tooltip, "Full set", "+20 nonmatching spell efficiency"
        );

        assertTrue(tooltip.indexOf("+20 nonmatching spell efficiency")
                == tooltip.indexOf("Full set") + 1);
    }
}
