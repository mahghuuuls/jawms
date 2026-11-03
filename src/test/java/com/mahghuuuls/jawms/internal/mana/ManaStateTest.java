package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.internal.modifier.ResolvedManaProfile;
import electroblob.wizardry.constants.Element;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaStateTest {

    @Test
    void staleGenerationCannotConsumeReplacementClaim() {
        ManaState state = new ManaState();
        ManaReservation stale = state.reserve(30);
        state.clearTransientLifecycleState();
        ManaReservation current = state.reserve(40);

        ManaService.releaseReservation(stale);

        assertFalse(state.hasPayableReservation(stale));
        assertThrows(IllegalStateException.class, () -> state.commit(stale));
        assertTrue(state.hasPayableReservation(current));
        assertEquals(20, state.getAvailableMana());
        state.commit(current);
        assertEquals(20, state.getCurrentMana());
    }

    @Test
    void unsafeProfileShrinkPreservesNestedReservationsUntilNormalRefresh() {
        ManaState state = new ManaState();
        ManaReservation outer = state.reserve(30);
        ManaReservation inner = state.reserve(20);

        assertFalse(state.applyEffectiveStats(25, 0.5D));
        assertEquals(60, state.getMaximumMana());
        assertEquals(10, state.drainUpTo(60));
        assertTrue(state.hasPayableReservation(inner));
        state.commit(inner);
        assertTrue(state.hasPayableReservation(outer));
        state.release(outer);

        assertTrue(state.applyEffectiveStats(25, 0.5D));
        assertEquals(25, state.getMaximumMana());
        assertEquals(25, state.getCurrentMana());
    }

    @Test
    void newStateStartsFull() {
        ManaState state = new ManaState();

        assertEquals(60, state.getCurrentMana());
        assertEquals(60, state.getMaximumMana());
        assertEquals(2.4D, state.getEffectiveRegeneration());
    }

    @Test
    void consumptionIsAtomicAndRestorationClamps() {
        ManaState state = new ManaState();

        assertTrue(state.tryConsume(30));
        assertFalse(state.tryConsume(31));
        assertEquals(30, state.getCurrentMana());
        assertEquals(30, state.restore(5000));
        assertEquals(60, state.getCurrentMana());
    }

    @Test
    void reservationProtectsSpellCostFromOtherConsumptionUntilCommit() {
        ManaState state = new ManaState();
        ManaReservation reservation = state.reserve(30);

        assertEquals(60, state.getCurrentMana());
        assertEquals(30, state.getAvailableMana());
        assertFalse(state.tryConsume(31));
        assertTrue(state.tryConsume(30));
        assertEquals(30, state.getCurrentMana());
        assertEquals(0, state.getAvailableMana());

        state.commit(reservation);

        assertEquals(0, state.getCurrentMana());
        assertEquals(0, state.getAvailableMana());
    }

    @Test
    void failedAndNestedReservationsReleaseWithoutVisibleConsumption() {
        ManaState state = new ManaState();
        ManaReservation outer = state.reserve(40);
        ManaReservation inner = state.reserve(20);

        assertEquals(0, state.getAvailableMana());
        state.release(inner);
        assertEquals(20, state.getAvailableMana());
        assertEquals(60, state.getCurrentMana());
        state.release(outer);
        assertEquals(60, state.getAvailableMana());
        assertEquals(60, state.getCurrentMana());
    }

    @Test
    void zeroReservationCommitsExactlyOnceWithoutChangingMana() {
        ManaState state = new ManaState();
        ManaReservation reservation = state.reserve(0);

        state.commit(reservation);

        assertEquals(60, state.getCurrentMana());
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
        assertEquals(60, state.getCurrentMana());
    }

    @Test
    void fractionalRegenerationAccumulatesWithoutExposingFractions() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        for (int tick = 0; tick < 39; tick++) {
            state.regenerateTick(0.5D);
        }
        assertEquals(58, state.getCurrentMana());

        state.regenerateTick(0.5D);
        assertEquals(59, state.getCurrentMana());
    }

    @Test
    void defaultRegenerationRestoresTwoWholeManaAfterTwentyTicks() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        int restored = 0;
        for (int tick = 0; tick < 20; tick++) {
            restored += state.regenerateTick(state.getEffectiveRegeneration());
        }
        assertEquals(2, restored);
        assertEquals(60, state.getCurrentMana());
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
        assertEquals(40, restored.getCurrentMana());

        saved.setInteger("currentMana", 5000);
        restored.deserializeIntrinsicState(saved);
        assertEquals(60, restored.getCurrentMana());
    }

    @Test
    void cloneCopiesOnlyBoundedCurrentManaAndAdvancesRevision() {
        ManaState original = new ManaState();
        original.tryConsume(25);
        ManaState clone = new ManaState();

        clone.copyFrom(original);

        assertEquals(35, clone.getCurrentMana());
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

    @Test
    void loadedManaIsBoundedOnlyAfterEquippedProfileIsRecalculated() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("currentMana", 90);
        ManaState restored = new ManaState();

        restored.deserializeIntrinsicState(saved);
        restored.applyEffectiveStats(100, 4.0D);

        assertEquals(90, restored.getCurrentMana());
    }

    @Test
    void activeContinuousCastBlocksRegenerationWhileOlderLockoutCountsDown() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.startRegenerationLockout(2L);
        state.beginContinuousCast(EnumHand.MAIN_HAND, spellId);

        assertFalse(state.advanceRegenerationGate());
        assertEquals(1L, state.getRemainingLockoutTicks());
        assertFalse(state.advanceRegenerationGate());
        assertEquals(0L, state.getRemainingLockoutTicks());
        assertFalse(state.advanceRegenerationGate());
        assertTrue(state.isRegenerationBlocked());
    }

    @Test
    void gatePermissionsBypassIndependentlyWhileCountdownStillAdvances() {
        ManaState state = new ManaState();
        state.applyResolvedProfile(new ResolvedManaProfile(
                50,
                2.0D,
                0.1D,
                2L,
                0.0D,
                new EnumMap<>(Element.class),
                true,
                false
        ));
        state.startRegenerationLockout(2L);
        assertTrue(state.advanceRegenerationGate());
        assertEquals(1L, state.getRemainingLockoutTicks());

        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.MAIN_HAND, spellId);
        assertFalse(state.advanceRegenerationGate());
        assertEquals(0L, state.getRemainingLockoutTicks());

        state.applyResolvedProfile(new ResolvedManaProfile(
                50,
                2.0D,
                0.1D,
                2L,
                0.0D,
                new EnumMap<>(Element.class),
                false,
                true
        ));
        assertTrue(state.advanceRegenerationGate());
    }

    @Test
    void successfulContinuousCastStartsFullLockoutOnlyWhenItEnds() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.OFF_HAND, spellId);
        state.markContinuousCastSuccessful(EnumHand.OFF_HAND, spellId);

        assertEquals(0L, state.getRemainingLockoutTicks());
        assertTrue(state.finishContinuousCast(40L));
        assertEquals(40L, state.getRemainingLockoutTicks());
        assertFalse(state.isContinuousCastActive());
    }

    @Test
    void failedContinuousStartDoesNotCreateLockout() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.MAIN_HAND, spellId);

        assertFalse(state.finishContinuousCast(40L));
        assertEquals(0L, state.getRemainingLockoutTicks());
        assertFalse(state.isRegenerationBlocked());
    }

    @Test
    void interruptionIsScopedToTheTrackedHandAndSpell() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.OFF_HAND, spellId);

        state.interruptContinuousCast(EnumHand.MAIN_HAND, spellId);
        assertFalse(state.wasContinuousCastInterrupted());
        assertFalse(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, spellId));
        state.interruptContinuousCast(EnumHand.OFF_HAND, new ResourceLocation("ebwizardry", "other"));
        assertFalse(state.wasContinuousCastInterrupted());
        state.interruptContinuousCast(EnumHand.OFF_HAND, spellId);
        assertTrue(state.wasContinuousCastInterrupted());
        assertTrue(state.isContinuousCastRetryBlocked(EnumHand.OFF_HAND, spellId));
    }

    @Test
    void manaExhaustionIsDistinctFromOtherInterruptionsAndScopedToTheCast() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.MAIN_HAND, spellId);

        state.exhaustContinuousCastMana(EnumHand.OFF_HAND, spellId);
        assertFalse(state.wasContinuousCastManaExhausted());
        assertFalse(state.isContinuousCastRetryBlocked(EnumHand.OFF_HAND, spellId));
        state.interruptContinuousCast(EnumHand.MAIN_HAND, spellId);
        assertTrue(state.wasContinuousCastInterrupted());
        assertFalse(state.wasContinuousCastManaExhausted());
        state.exhaustContinuousCastMana(EnumHand.MAIN_HAND, spellId);
        assertTrue(state.wasContinuousCastManaExhausted());
        assertTrue(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, spellId));
    }

    @Test
    void continuousRetryBlockRequiresMatchingClientRelease() {
        ManaState state = new ManaState();
        ResourceLocation flameRay = new ResourceLocation("ebwizardry", "flame_ray");
        ResourceLocation grapple = new ResourceLocation("ebwizardry", "grapple");

        state.blockContinuousCastRetry(EnumHand.MAIN_HAND, flameRay);

        assertTrue(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, flameRay));
        assertFalse(state.isContinuousCastRetryBlocked(EnumHand.OFF_HAND, flameRay));
        assertFalse(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, grapple));

        state.releaseContinuousCastRetryBlock(EnumHand.OFF_HAND, flameRay);
        assertTrue(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, flameRay));

        state.releaseContinuousCastRetryBlock(EnumHand.MAIN_HAND, flameRay);
        assertFalse(state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, flameRay));
    }

    @Test
    void zeroDurationStillBlocksWhileActiveButNotAfterSuccessfulStop() {
        ManaState state = new ManaState();
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        state.beginContinuousCast(EnumHand.MAIN_HAND, spellId);
        state.markContinuousCastSuccessful(EnumHand.MAIN_HAND, spellId);

        assertFalse(state.advanceRegenerationGate());
        assertTrue(state.finishContinuousCast(0L));
        assertTrue(state.advanceRegenerationGate());
    }

    @Test
    void continuousStateIsTransientAcrossLifecycleResetCloneAndPersistence() {
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "test_continuous");
        ManaState original = new ManaState();
        original.beginContinuousCast(EnumHand.MAIN_HAND, spellId);
        original.markContinuousCastSuccessful(EnumHand.MAIN_HAND, spellId);

        ManaState clone = new ManaState();
        clone.copyFrom(original);
        assertFalse(clone.isContinuousCastActive());

        ManaState restored = new ManaState();
        restored.beginContinuousCast(EnumHand.MAIN_HAND, spellId);
        restored.deserializeIntrinsicState(original.serializeIntrinsicState());
        assertFalse(restored.isContinuousCastActive());

        original.clearTransientLifecycleState();
        assertFalse(original.isContinuousCastActive());
        assertFalse(original.isRegenerationBlocked());
    }
}
