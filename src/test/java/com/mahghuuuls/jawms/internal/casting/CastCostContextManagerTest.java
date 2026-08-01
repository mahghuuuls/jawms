package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CastCostContextManagerTest {

    @Test
    void freezesByPlayerSideHandAndSpellUntilExplicitCleanup() {
        UUID player = UUID.randomUUID();
        ResourceLocation spell = new ResourceLocation("test", "beam");

        assertEquals(40, CastCostContextManager.captureOrGet(
                player, false, EnumHand.MAIN_HAND, spell, 40, 50, 1, 0.5, 25, 100
        ));
        assertEquals(40, CastCostContextManager.captureOrGet(
                player, false, EnumHand.MAIN_HAND, spell, 90, 50, 2, 1.5, 0, 100
        ));
        assertEquals(90, CastCostContextManager.captureOrGet(
                player, false, EnumHand.OFF_HAND, spell, 90,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        ));
        CastCostContextManager.clear(player, false, EnumHand.MAIN_HAND);
        assertEquals(90, CastCostContextManager.captureOrGet(
                player, false, EnumHand.MAIN_HAND, spell, 90,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        ));
        assertEquals(70, CastCostContextManager.captureOrGet(
                player, true, EnumHand.MAIN_HAND, spell, 70,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        ));
        CastCostContextManager.clearClientSide();
        assertEquals(90, CastCostContextManager.captureOrGet(
                player, true, EnumHand.MAIN_HAND, spell, 90,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        ));
        CastCostContextManager.clearClientSide();
        CastCostContextManager.clear(player, false, EnumHand.MAIN_HAND);
        CastCostContextManager.clear(player, false, EnumHand.OFF_HAND);
    }
}
