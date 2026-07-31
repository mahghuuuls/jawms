package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ManaPublicApiValuesTest {

    @Test
    void stateIsImmutableComparableAndReportsSeconds() {
        ManaPublicState first = state(10, 50, 2.0D, 40L, 20L, false);
        ManaPublicState equal = state(10, 50, 2.0D, 40L, 20L, false);

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertEquals(2.0D, first.getEffectiveLockoutSeconds());
        assertEquals(1.0D, first.getRemainingLockoutSeconds());
        assertFalse(first.isRegenerationEligible());
        assertThrows(IllegalArgumentException.class,
                () -> state(51, 50, 2.0D, 40L, 0L, true));
    }

    @Test
    void mutationResultPreservesCauseStatesAndActualAmount() {
        ManaPublicState oldState = state(20, 50, 2.0D, 40L, 0L, true);
        ManaPublicState newState = state(5, 50, 2.0D, 40L, 0L, true);
        ResourceLocation cause = new ResourceLocation("addon", "drain");

        ManaMutationResult result = new ManaMutationResult(
                true,
                15,
                -15,
                cause,
                oldState,
                newState
        );

        assertTrue(result.isSuccessful());
        assertEquals(15, result.getActualAmount());
        assertEquals(-15, result.getActualDelta());
        assertEquals(cause, result.getCause());
        assertEquals(oldState, result.getOldState());
        assertEquals(newState, result.getNewState());
    }

    @Test
    void stateChangeEventIsNonCancellableAndReportsTruthfulDelta() {
        EntityPlayer player = mock(EntityPlayer.class);
        ManaPublicState oldState = state(1, 50, 2.0D, 40L, 0L, true);
        ManaPublicState newState = state(0, 50, 2.0D, 40L, 40L, false);
        ManaStateChangeEvent event = new ManaStateChangeEvent(
                player,
                oldState,
                newState,
                ManaCauses.SPELL_COST
        );

        assertFalse(event.isCancelable());
        assertEquals(player, event.getPlayer());
        assertEquals(-1, event.getActualManaDelta());
        assertEquals(oldState, event.getOldState());
        assertEquals(newState, event.getNewState());
        assertEquals(ManaCauses.SPELL_COST, event.getCause());
    }

    private static ManaPublicState state(
            int current,
            int maximum,
            double regeneration,
            long effectiveLockout,
            long remainingLockout,
            boolean eligible
    ) {
        return new ManaPublicState(
                current,
                maximum,
                regeneration,
                effectiveLockout,
                remainingLockout,
                false,
                false,
                false,
                eligible
        );
    }
}
