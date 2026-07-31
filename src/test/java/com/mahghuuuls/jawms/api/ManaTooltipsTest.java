package com.mahghuuuls.jawms.api;

import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManaTooltipsTest {

    @Test
    void formatsApprovedConciseColoredLabels() {
        List<String> lines = ManaTooltips.format(ManaContribution.builder()
                .flatMaximumMana(-5)
                .maximumManaIncrease(10)
                .flatRegeneration(0.5D)
                .regenerationReduction(25)
                .flatLockoutSeconds(1)
                .lockoutReduction(50)
                .build());

        assertEquals(TextFormatting.RED + "-5 Mana", lines.get(0));
        assertEquals(TextFormatting.BLUE + "+10% Mana", lines.get(1));
        assertEquals(TextFormatting.BLUE + "+0.5 Mana regen", lines.get(2));
        assertEquals(TextFormatting.RED + "-25% Mana regen", lines.get(3));
        assertEquals(TextFormatting.RED + "+1 Mana regen delay", lines.get(4));
        assertEquals(TextFormatting.BLUE + "-50% Mana regen delay", lines.get(5));
        assertThrows(UnsupportedOperationException.class, () -> lines.add("bad"));
    }
}
