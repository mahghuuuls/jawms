package com.mahghuuuls.jawms.client;

import net.minecraft.util.text.translation.I18n;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManaItemTooltipHandlerTest {

    @Test
    void removesOnlyVanillaDurabilityLineFromWizardryWands() {
        String durability = I18n.translateToLocalFormatted("item.durability", 123, 700);
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                "Current Spell: Magic Missile",
                "Progression: 14/1500",
                durability,
                "ebwizardry:magic_wand"
        ));

        ManaItemTooltipHandler.removeWandDurabilityLine(tooltip, true, 123, 700);

        assertEquals(Arrays.asList(
                "Current Spell: Magic Missile",
                "Progression: 14/1500",
                "ebwizardry:magic_wand"
        ), tooltip);
    }

    @Test
    void leavesOrdinaryDurabilityTooltipUntouched() {
        String durability = I18n.translateToLocalFormatted("item.durability", 123, 700);
        List<String> tooltip = new ArrayList<>(Arrays.asList("Ordinary item", durability));

        ManaItemTooltipHandler.removeWandDurabilityLine(tooltip, false, 123, 700);

        assertEquals(Arrays.asList("Ordinary item", durability), tooltip);
    }
}
