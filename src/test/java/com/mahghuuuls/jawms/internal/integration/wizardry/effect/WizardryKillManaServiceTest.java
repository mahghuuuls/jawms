package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WizardryKillManaServiceTest {

    @Test
    void defaultSiphonLevelsAndSiphoningUseApprovedCeiling() {
        assertEquals(3, restore(1, false));
        assertEquals(6, restore(2, false));
        assertEquals(9, restore(3, false));
        assertEquals(4, restore(1, true));
        assertEquals(8, restore(2, true));
        assertEquals(12, restore(3, true));
    }

    @Test
    void aggregatedLevelsResolveBeforeOneSiphoningMultiplier() {
        assertEquals(9, restore(3, false));
        assertEquals(12, restore(4, false));
        assertEquals(12, restore(3, true));
        assertEquals(16, restore(4, true));
        assertEquals(12, WizardryKillManaService.calculateSiphonRestoration(
                10, 1, true, 20.0D
        ));
    }

    @Test
    void zeroNegativeAndOverflowInputsAreBounded() {
        assertEquals(0, WizardryKillManaService.calculateSiphonRestoration(
                -1, 3, true, 30.0D
        ));
        assertEquals(0, WizardryKillManaService.calculateSiphonRestoration(
                3, -1, true, 30.0D
        ));
        assertEquals(Integer.MAX_VALUE,
                WizardryKillManaService.calculateSiphonRestoration(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, Double.MAX_VALUE
                ));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void logoutRemovesRetainedDevelopmentDiagnostic() throws Exception {
        UUID playerId = UUID.randomUUID();
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(playerId);

        Field field = WizardryKillManaService.class.getDeclaredField("LAST_EFFECTS");
        field.setAccessible(true);
        Map records = (Map) field.get(null);
        records.put(playerId, null);
        assertTrue(records.containsKey(playerId));

        new WizardryKillManaService().onPlayerLoggedOut(
                new PlayerEvent.PlayerLoggedOutEvent(player)
        );

        assertFalse(records.containsKey(playerId));
    }

    private static int restore(int level, boolean siphoning) {
        return WizardryKillManaService.calculateSiphonRestoration(
                level, 3, siphoning, 30.0D
        );
    }
}
