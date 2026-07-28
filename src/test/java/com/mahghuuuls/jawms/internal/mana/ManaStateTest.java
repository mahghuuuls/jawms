package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaStateTest {

    @Test
    void newStateStartsFull() {
        ManaState state = new ManaState();

        assertEquals(50, state.getCurrentMana());
        assertEquals(50, state.getMaximumMana());
        assertEquals(2.0D, state.getEffectiveRegeneration());
    }

    @Test
    void consumptionIsAtomicAndRestorationClamps() {
        ManaState state = new ManaState();

        assertTrue(state.tryConsume(30));
        assertFalse(state.tryConsume(30));
        assertEquals(20, state.getCurrentMana());
        assertEquals(30, state.restore(5000));
        assertEquals(50, state.getCurrentMana());
    }

    @Test
    void reservationProtectsSpellCostFromOtherConsumptionUntilCommit() {
        ManaState state = new ManaState();
        ManaReservation reservation = state.reserve(30);

        assertEquals(50, state.getCurrentMana());
        assertEquals(20, state.getAvailableMana());
        assertFalse(state.tryConsume(21));
        assertTrue(state.tryConsume(20));
        assertEquals(30, state.getCurrentMana());
        assertEquals(0, state.getAvailableMana());

        state.commit(reservation);

        assertEquals(0, state.getCurrentMana());
        assertEquals(0, state.getAvailableMana());
    }

    @Test
    void failedAndNestedReservationsReleaseWithoutVisibleConsumption() {
        ManaState state = new ManaState();
        ManaReservation outer = state.reserve(30);
        ManaReservation inner = state.reserve(20);

        assertEquals(0, state.getAvailableMana());
        state.release(inner);
        assertEquals(20, state.getAvailableMana());
        assertEquals(50, state.getCurrentMana());
        state.release(outer);
        assertEquals(50, state.getAvailableMana());
        assertEquals(50, state.getCurrentMana());
    }

    @Test
    void zeroReservationCommitsExactlyOnceWithoutChangingMana() {
        ManaState state = new ManaState();
        ManaReservation reservation = state.reserve(0);

        state.commit(reservation);

        assertEquals(50, state.getCurrentMana());
        assertThrows(IllegalStateException.class, () -> state.commit(reservation));
        assertThrows(IllegalStateException.class, () -> state.release(reservation));
    }

    @Test
    void repeatedInsufficientNoticeIsRateLimitedUnlessValuesChange() {
        ManaState state = new ManaState();

        assertTrue(state.shouldNotifyInsufficient(100L, 30, 20));
        assertFalse(state.shouldNotifyInsufficient(109L, 30, 20));
        assertTrue(state.shouldNotifyInsufficient(109L, 31, 20));
        assertFalse(state.shouldNotifyInsufficient(110L, 31, 20));
        assertTrue(state.shouldNotifyInsufficient(119L, 31, 20));
    }

    @Test
    void negativeMutationsAreRejected() {
        ManaState state = new ManaState();

        assertThrows(IllegalArgumentException.class, () -> state.restore(-1));
        assertThrows(IllegalArgumentException.class, () -> state.tryConsume(-1));
        assertEquals(50, state.getCurrentMana());
    }

    @Test
    void fractionalRegenerationAccumulatesWithoutExposingFractions() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        for (int tick = 0; tick < 39; tick++) {
            state.regenerateTick(0.5D);
        }
        assertEquals(48, state.getCurrentMana());

        state.regenerateTick(0.5D);
        assertEquals(49, state.getCurrentMana());
    }

    @Test
    void defaultRegenerationRestoresTwoManaAfterTwentyTicks() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        for (int tick = 0; tick < 9; tick++) {
            assertEquals(0, state.regenerateTick(state.getEffectiveRegeneration()));
        }
        assertEquals(1, state.regenerateTick(state.getEffectiveRegeneration()));
        for (int tick = 0; tick < 9; tick++) {
            assertEquals(0, state.regenerateTick(state.getEffectiveRegeneration()));
        }
        assertEquals(1, state.regenerateTick(state.getEffectiveRegeneration()));
        assertEquals(50, state.getCurrentMana());
    }

    @Test
    void persistenceStoresOnlyBoundedCurrentMana() {
        ManaState original = new ManaState();
        original.tryConsume(20);
        original.regenerateTick(0.5D);

        NBTTagCompound saved = original.serializeIntrinsicState();
        assertEquals(1, saved.getKeySet().size());
        assertTrue(saved.hasKey("currentMana"));

        ManaState restored = new ManaState();
        restored.deserializeIntrinsicState(saved);
        assertEquals(30, restored.getCurrentMana());

        saved.setInteger("currentMana", 5000);
        restored.deserializeIntrinsicState(saved);
        assertEquals(50, restored.getCurrentMana());
    }

    @Test
    void cloneCopiesOnlyBoundedCurrentManaAndAdvancesRevision() {
        ManaState original = new ManaState();
        original.tryConsume(25);
        ManaState clone = new ManaState();

        clone.copyFrom(original);

        assertEquals(25, clone.getCurrentMana());
        assertTrue(clone.getRevision() > original.getRevision());
    }

    @Test
    void invalidRegenerationRatesAreRejected() {
        ManaState state = new ManaState();

        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(-0.1D));
        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(Double.POSITIVE_INFINITY));
    }

    @Test
    void applyingLowerEffectiveMaximumClampsCurrentAndResetsRate() {
        ManaState state = new ManaState();

        assertTrue(state.applyEffectiveStats(20, 0.5D));

        assertEquals(20, state.getCurrentMana());
        assertEquals(20, state.getMaximumMana());
        assertEquals(0.5D, state.getEffectiveRegeneration());
        assertFalse(state.applyEffectiveStats(20, 0.5D));
    }

    @Test
    void lockoutRestartsCountsDownAndShorterReloadClampsWithoutLongerExtension() {
        ManaState state = new ManaState();
        state.startRegenerationLockout(40L);
        assertTrue(state.isRegenerationBlocked());

        state.advanceRegenerationGate();
        assertEquals(39L, state.getRemainingLockoutTicks());
        state.startRegenerationLockout(40L);
        assertEquals(40L, state.getRemainingLockoutTicks());

        state.clampLockoutAfterReload(20L);
        assertEquals(20L, state.getRemainingLockoutTicks());
        state.clampLockoutAfterReload(80L);
        assertEquals(20L, state.getRemainingLockoutTicks());
        state.clampLockoutAfterReload(0L);
        assertFalse(state.isRegenerationBlocked());
    }

    @Test
    void lockoutBlocksExactlyTheConfiguredNumberOfGateAdvances() {
        ManaState state = new ManaState();
        state.startRegenerationLockout(2L);

        assertFalse(state.advanceRegenerationGate());
        assertEquals(1L, state.getRemainingLockoutTicks());
        assertFalse(state.advanceRegenerationGate());
        assertEquals(0L, state.getRemainingLockoutTicks());
        assertTrue(state.advanceRegenerationGate());

        state.startRegenerationLockout(0L);
        assertTrue(state.advanceRegenerationGate());
    }

    @Test
    void lockoutStateIsTransientAcrossCloneAndPersistence() {
        ManaState original = new ManaState();
        original.startRegenerationLockout(40L);
        ManaState clone = new ManaState();
        clone.copyFrom(original);
        assertFalse(clone.isRegenerationBlocked());

        ManaState restored = new ManaState();
        restored.startRegenerationLockout(40L);
        restored.deserializeIntrinsicState(original.serializeIntrinsicState());
        assertFalse(restored.isRegenerationBlocked());
    }
}
