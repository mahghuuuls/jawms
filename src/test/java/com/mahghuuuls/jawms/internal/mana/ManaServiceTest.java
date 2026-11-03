package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.modifier.ResolvedManaProfile;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.casting.CastCostContextTestProbe;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.DemonicSealPolicy;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class ManaServiceTest {

    private final List<Object> registeredListeners = new ArrayList<>();

    @AfterEach
    void unregisterListeners() {
        for (Object listener : registeredListeners) {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    @Test
    void mutationsRejectOffThreadServerPlayersBeforeCapabilityAccess() {
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(false);
        ManaService service = new ManaService();
        ResourceLocation cause = new ResourceLocation("test", "mutation");

        assertThrows(IllegalStateException.class, () -> service.restoreMana(player, 1, cause));
        assertThrows(IllegalStateException.class, () -> service.consumeMana(player, 1, cause));
        assertThrows(IllegalStateException.class, () -> service.drainMana(player, 1, cause));
        assertThrows(IllegalStateException.class, () -> service.setCurrentMana(player, 1, cause));
        assertThrows(IllegalStateException.class,
                () -> service.startRegenerationLockout(player, cause));

        verify(player, never()).getCapability(any(), any());
    }

    @Test
    void lockoutRejectsLogicalClientPlayersBeforeCapabilityAccess() {
        EntityPlayer player = mock(EntityPlayer.class);
        player.world = mock(World.class);

        assertThrows(IllegalStateException.class,
                () -> new ManaService().startRegenerationLockout(
                        player,
                        new ResourceLocation("test", "client_side")
                ));

        verify(player, never()).getCapability(any(), any());
    }

    @Test
    void lockoutRejectsMissingCapabilityWithoutPublishingAnEvent() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        RecordingListener listener = register(new RecordingListener(player));

        assertThrows(IllegalStateException.class,
                () -> new ManaService().startRegenerationLockout(
                        player,
                        new ResourceLocation("test", "missing_capability")
                ));

        assertEquals(0, listener.events.size());
        verify(player).getCapability(ManaCapability.INSTANCE, null);
    }

    @Test
    void lockoutOperationIsDefaultCompatibleForExistingServiceImplementations()
            throws NoSuchMethodException {
        assertTrue(IManaService.class.getMethod(
                "startRegenerationLockout",
                EntityPlayer.class,
                ResourceLocation.class
        ).isDefault());

        IManaService legacyService = mock(IManaService.class, CALLS_REAL_METHODS);
        EntityPlayer player = mock(EntityPlayer.class);
        assertThrows(UnsupportedOperationException.class,
                () -> legacyService.startRegenerationLockout(
                        player,
                        new ResourceLocation("test", "legacy_fallback")
                ));
        verifyNoInteractions(player);
    }

    @Test
    void explicitLockoutStartsAndRestartsForCreativePlayersWithCallerCause() {
        PlayerFixture fixture = playerFixture();
        when(fixture.player.isCreative()).thenReturn(true);
        ManaService service = new ManaService();
        RecordingListener listener = register(new RecordingListener(fixture.player));
        ResourceLocation cause = new ResourceLocation("testaddon", "special_ability");

        assertEquals(40L, service.startRegenerationLockout(fixture.player, cause));
        assertEquals(40L, fixture.state.getRemainingLockoutTicks());
        assertEquals(40L, service.startRegenerationLockout(fixture.player, cause));
        fixture.state.applyResolvedProfile(profileWithLockout(20L));
        assertEquals(20L, service.startRegenerationLockout(fixture.player, cause));

        assertEquals(3, listener.events.size());
        assertEquals(cause, listener.events.get(0).getCause());
        assertEquals(0L, listener.events.get(0).getOldState().getRemainingLockoutTicks());
        assertEquals(40L, listener.events.get(0).getNewState().getRemainingLockoutTicks());
        assertEquals(40L, listener.events.get(1).getOldState().getRemainingLockoutTicks());
        assertEquals(40L, listener.events.get(1).getNewState().getRemainingLockoutTicks());
        assertEquals(cause, listener.events.get(1).getCause());
        assertEquals(40L, listener.events.get(2).getOldState().getRemainingLockoutTicks());
        assertEquals(20L, listener.events.get(2).getNewState().getRemainingLockoutTicks());
        assertEquals(cause, listener.events.get(2).getCause());
    }

    @Test
    void zeroDurationClearsActiveLockoutAndIsSilentWhenAlreadyInactive() {
        PlayerFixture fixture = playerFixture();
        fixture.state.applyResolvedProfile(profileWithLockout(0L));
        fixture.state.startRegenerationLockout(12L);
        ManaService service = new ManaService();
        RecordingListener listener = register(new RecordingListener(fixture.player));
        ResourceLocation cause = new ResourceLocation("testaddon", "zero_lockout");

        assertEquals(0L, service.startRegenerationLockout(fixture.player, cause));
        assertEquals(0L, fixture.state.getRemainingLockoutTicks());
        assertEquals(1, listener.events.size());
        assertEquals(cause, listener.events.get(0).getCause());
        assertEquals(12L, listener.events.get(0).getOldState().getRemainingLockoutTicks());
        assertEquals(0L, listener.events.get(0).getNewState().getRemainingLockoutTicks());
        assertEquals(0, listener.events.get(0).getActualManaDelta());

        assertEquals(0L, service.startRegenerationLockout(fixture.player, cause));
        assertEquals(1, listener.events.size());
    }

    @Test
    void explicitLockoutUsesAcceptedProfileWithoutApplyingPendingLoadedState() {
        PlayerFixture fixture = playerFixture();
        net.minecraft.nbt.NBTTagCompound pending = new net.minecraft.nbt.NBTTagCompound();
        pending.setInteger("currentMana", 7);
        fixture.state.deserializeIntrinsicState(pending);
        assertTrue(fixture.state.hasPendingLoadedMana());

        long applied = new ManaService().startRegenerationLockout(
                fixture.player,
                new ResourceLocation("testaddon", "accepted_profile")
        );

        assertEquals(40L, applied);
        assertEquals(40L, fixture.state.getRemainingLockoutTicks());
        assertTrue(fixture.state.hasPendingLoadedMana());
        assertEquals(7, fixture.state.getCurrentMana());
    }

    @Test
    void explicitLockoutPreservesManaReservationsCastingAndHunger() {
        PlayerFixture fixture = playerFixture();
        ResourceLocation spell = new ResourceLocation("ebwizardry", "flame_ray");
        ManaReservation reservation = ManaService.reserveMana(fixture.player, 5);
        ManaService.beginContinuousCast(fixture.player, EnumHand.MAIN_HAND, spell);
        fixture.state.blockContinuousCastRetry(EnumHand.MAIN_HAND, spell);
        CastCostContextTestProbe.capture(
                fixture.player.getUniqueID(), EnumHand.MAIN_HAND, spell, 13
        );
        DemonicSealPolicy paymentPolicy = CastPaymentContextManager.capture(
                fixture.player,
                EnumHand.MAIN_HAND,
                GameplayRules.initialDefaults().getWizardryIntegrationRules()
        );
        String costContextBefore = CastCostContextManager.describe(
                fixture.player, EnumHand.MAIN_HAND
        );
        String paymentContextBefore = CastPaymentContextManager.describe(
                fixture.player, EnumHand.MAIN_HAND
        );
        net.minecraft.nbt.NBTTagCompound persistedBefore =
                fixture.state.serializeIntrinsicState();
        try {
            long applied = new ManaService().startRegenerationLockout(
                    fixture.player,
                    new ResourceLocation("testaddon", "non_casting_ability")
            );

            assertEquals(40L, applied);
            assertEquals(60, fixture.state.getCurrentMana());
            assertEquals(55, fixture.state.getAvailableMana());
            assertTrue(reservation.isActive());
            assertTrue(fixture.state.isContinuousCastActive());
            assertTrue(fixture.state.isContinuousCastRetryBlocked(EnumHand.MAIN_HAND, spell));
            assertEquals(persistedBefore, fixture.state.serializeIntrinsicState());
            assertEquals(costContextBefore, CastCostContextManager.describe(
                    fixture.player, EnumHand.MAIN_HAND
            ));
            assertEquals(paymentContextBefore, CastPaymentContextManager.describe(
                    fixture.player, EnumHand.MAIN_HAND
            ));
            assertSame(paymentPolicy, CastPaymentContextManager.get(
                    fixture.player, EnumHand.MAIN_HAND
            ));
            verify(fixture.player, never()).getFoodStats();
        } finally {
            CastCostContextManager.clear(fixture.player, EnumHand.MAIN_HAND);
            CastPaymentContextManager.clear(fixture.player, EnumHand.MAIN_HAND);
            ManaService.releaseReservation(reservation);
        }
    }

    @Test
    void bypassPermissionDoesNotStopTheLockoutCountdown() {
        PlayerFixture fixture = playerFixture();
        fixture.state.applyResolvedProfile(new ResolvedManaProfile(
                60, 2.4D, 2.0D, 40L, 0.0D, Collections.emptyMap(), true, false
        ));

        assertEquals(40L, new ManaService().startRegenerationLockout(
                fixture.player,
                new ResourceLocation("testaddon", "bypass_countdown")
        ));
        assertTrue(fixture.state.advanceRegenerationGate());
        assertEquals(39L, fixture.state.getRemainingLockoutTicks());
    }

    @Test
    void explicitLockoutReturnsCapturedDurationBeforeNestedListenersRun() {
        PlayerFixture fixture = playerFixture();
        register(new ClearingLockoutListener(fixture.player, fixture.state));

        long applied = new ManaService().startRegenerationLockout(
                fixture.player,
                new ResourceLocation("testaddon", "nested_listener")
        );

        assertEquals(40L, applied);
        assertEquals(0L, fixture.state.getRemainingLockoutTicks());
    }

    @Test
    void readsRejectOffThreadServerPlayersBeforeCapabilityAccess() {
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(false);
        ManaService service = new ManaService();

        assertThrows(IllegalStateException.class, () -> service.getState(player));
        assertThrows(IllegalStateException.class, () -> service.getCurrentMana(player));
        assertThrows(IllegalStateException.class, () -> service.getMaximumMana(player));

        verify(player, never()).getCapability(any(), any());
    }

    @Test
    void boundedMutationsReturnActualOutcomesAndPostCausedEvents() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        RecordingListener listener = register(new RecordingListener(fixture.player));
        ResourceLocation cause = new ResourceLocation("testaddon", "bounded_mutation");

        ManaMutationResult set = service.setCurrentMana(fixture.player, 20, cause);
        ManaMutationResult failedConsume = service.consumeMana(fixture.player, 30, cause);
        ManaMutationResult exactConsume = service.consumeMana(fixture.player, 20, cause);
        ManaMutationResult restore = service.restoreMana(fixture.player, 5000, cause);
        ManaMutationResult drain = service.drainMana(fixture.player, 5000, cause);

        assertTrue(set.isSuccessful());
        assertEquals(-40, set.getActualDelta());
        assertFalse(failedConsume.isSuccessful());
        assertEquals(ManaMutationFailure.INSUFFICIENT_MANA, failedConsume.getFailure());
        assertEquals(30, failedConsume.getRequestedAmount());
        assertEquals(0, failedConsume.getActualDelta());
        assertEquals(cause, failedConsume.getCause());
        assertEquals(failedConsume.getOldState(), failedConsume.getNewState());
        assertTrue(exactConsume.isSuccessful());
        assertEquals(ManaMutationFailure.NONE, exactConsume.getFailure());
        assertEquals(-20, exactConsume.getActualDelta());
        assertEquals(60, restore.getActualAmount());
        assertEquals(60, drain.getActualAmount());
        assertEquals(0, service.getCurrentMana(fixture.player));
        assertEquals(4, listener.events.size());
        assertEquals(cause, listener.events.get(0).getCause());
        assertEquals(-40, listener.events.get(0).getActualManaDelta());
    }

    @Test
    void successfulBoundedNoOpsReportNoFailure() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        ResourceLocation cause = new ResourceLocation("testaddon", "bounded_noop");

        assertEquals(ManaMutationFailure.NONE,
                service.setCurrentMana(fixture.player, 60, cause).getFailure());
        assertEquals(ManaMutationFailure.NONE,
                service.restoreMana(fixture.player, 5, cause).getFailure());
        assertEquals(ManaMutationFailure.NONE,
                service.consumeMana(fixture.player, 0, cause).getFailure());
        assertEquals(ManaMutationFailure.NONE,
                service.drainMana(fixture.player, 0, cause).getFailure());
    }

    @Test
    void explicitMutationAppliesToCreativePlayersWithoutStartingLockout() {
        PlayerFixture fixture = playerFixture();
        when(fixture.player.isCreative()).thenReturn(true);

        ManaMutationResult result = new ManaService().consumeMana(
                fixture.player,
                10,
                new ResourceLocation("test", "creative_mutation")
        );

        assertTrue(result.isSuccessful());
        assertEquals(50, result.getNewState().getCurrentMana());
        assertEquals(0L, result.getNewState().getRemainingLockoutTicks());
    }

    @Test
    void setCannotUndercutAnActiveCastReservation() {
        PlayerFixture fixture = playerFixture();
        ManaReservation reservation = ManaService.reserveMana(fixture.player, 5);

        assertThrows(IllegalStateException.class, () -> new ManaService().setCurrentMana(
                fixture.player,
                0,
                new ResourceLocation("test", "reserved_set")
        ));
        assertEquals(60, fixture.state.getCurrentMana());
        assertEquals(55, fixture.state.getAvailableMana());

        ManaService.completeSuccessfulWandCast(fixture.player, reservation);
        assertEquals(55, fixture.state.getCurrentMana());
        assertEquals(55, fixture.state.getAvailableMana());
    }

    @Test
    void invalidAmountsAndCauseDoNotChangeMana() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        ResourceLocation cause = new ResourceLocation("test", "invalid");
        fixture.state.startRegenerationLockout(9L);
        ManaPublicState before = ManaService.snapshot(fixture.state);

        assertThrows(IllegalArgumentException.class,
                () -> service.restoreMana(fixture.player, -1, cause));
        assertThrows(IllegalArgumentException.class,
                () -> service.consumeMana(fixture.player, -1, cause));
        assertThrows(IllegalArgumentException.class,
                () -> service.drainMana(fixture.player, -1, cause));
        assertThrows(IllegalArgumentException.class,
                () -> service.setCurrentMana(fixture.player, -1, cause));
        assertThrows(NullPointerException.class,
                () -> service.restoreMana(fixture.player, 1, null));
        assertThrows(NullPointerException.class,
                () -> service.startRegenerationLockout(fixture.player, null));
        assertThrows(NullPointerException.class,
                () -> service.startRegenerationLockout(null, cause));

        assertEquals(before, ManaService.snapshot(fixture.state));
    }

    @Test
    void publicStateExposesEffectiveAndTransientValues() {
        PlayerFixture fixture = playerFixture();
        fixture.state.tryConsume(1);
        fixture.state.startRegenerationLockout(20L);
        ManaPublicState state = new ManaService().getState(fixture.player);

        assertEquals(59, state.getCurrentMana());
        assertEquals(60, state.getMaximumMana());
        assertEquals(2.4D, state.getEffectiveRegeneration());
        assertEquals(40L, state.getEffectiveLockoutTicks());
        assertEquals(20L, state.getRemainingLockoutTicks());
        assertFalse(state.isRegenerationEligible());
        assertFalse(state.canRegenerateDuringPostCastLockout());
        assertFalse(state.canRegenerateDuringContinuousCasting());
    }

    @Test
    void providerEvaluationGuardRejectsMutationWithoutBlockingQueries() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        ResourceLocation cause = new ResourceLocation("test", "guarded");
        ResourceLocation spell = new ResourceLocation("ebwizardry", "flame_ray");
        ManaReservation reservation = ManaService.reserveMana(fixture.player, 5);
        ManaService.beginContinuousCast(fixture.player, EnumHand.MAIN_HAND, spell);
        fixture.state.blockContinuousCastRetry(EnumHand.MAIN_HAND, spell);
        CastCostContextTestProbe.capture(
                fixture.player.getUniqueID(), EnumHand.MAIN_HAND, spell, 13
        );
        DemonicSealPolicy paymentPolicy = CastPaymentContextManager.capture(
                fixture.player,
                EnumHand.MAIN_HAND,
                GameplayRules.initialDefaults().getWizardryIntegrationRules()
        );
        fixture.state.startRegenerationLockout(11L);
        ManaPublicState before = ManaService.snapshot(fixture.state);
        int availableBefore = fixture.state.getAvailableMana();
        net.minecraft.nbt.NBTTagCompound persistedBefore =
                fixture.state.serializeIntrinsicState();
        String costContextBefore = CastCostContextManager.describe(
                fixture.player, EnumHand.MAIN_HAND
        );
        String paymentContextBefore = CastPaymentContextManager.describe(
                fixture.player, EnumHand.MAIN_HAND
        );

        try {
            try (ManaEvaluationGuard.Scope ignored =
                         ManaEvaluationGuard.begin(fixture.player.getUniqueID())) {
                assertEquals(60, service.getCurrentMana(fixture.player));
                assertThrows(IllegalStateException.class,
                        () -> service.consumeMana(fixture.player, 1, cause));
                assertThrows(IllegalStateException.class,
                        () -> service.startRegenerationLockout(fixture.player, cause));
            }

            assertEquals(before, ManaService.snapshot(fixture.state));
            assertEquals(availableBefore, fixture.state.getAvailableMana());
            assertTrue(reservation.isActive());
            assertTrue(fixture.state.isContinuousCastRetryBlocked(
                    EnumHand.MAIN_HAND, spell
            ));
            assertEquals(persistedBefore, fixture.state.serializeIntrinsicState());
            assertEquals(costContextBefore, CastCostContextManager.describe(
                    fixture.player, EnumHand.MAIN_HAND
            ));
            assertEquals(paymentContextBefore, CastPaymentContextManager.describe(
                    fixture.player, EnumHand.MAIN_HAND
            ));
            assertSame(paymentPolicy, CastPaymentContextManager.get(
                    fixture.player, EnumHand.MAIN_HAND
            ));
        } finally {
            CastCostContextManager.clear(fixture.player, EnumHand.MAIN_HAND);
            CastPaymentContextManager.clear(fixture.player, EnumHand.MAIN_HAND);
            ManaService.releaseReservation(reservation);
        }
    }

    @Test
    void ordinaryCountdownTicksDoNotFloodEventsButEndIsObservableEvenWhenFull() {
        PlayerFixture fixture = playerFixture();
        fixture.state.startRegenerationLockout(2L);
        RecordingListener listener = register(new RecordingListener(fixture.player));

        ManaService.tickPlayer(fixture.player, GameplayRules.initialDefaults());
        assertEquals(0, listener.events.size());
        ManaService.tickPlayer(fixture.player, GameplayRules.initialDefaults());

        assertEquals(1, listener.events.size());
        assertEquals(new ResourceLocation("jawms", "lockout_end"),
                listener.events.get(0).getCause());
        assertEquals(0L, listener.events.get(0).getNewState().getRemainingLockoutTicks());
    }

    @Test
    void deferredReloadPreservesAcceptedExplanationAndUsesLatestSafeEvaluation() {
        PlayerFixture fixture = playerFixture();
        GameplayRules original = GameplayRules.initialDefaults();
        GameplayRules first = rulesWithMaximum(original, 11L, 20);
        GameplayRules latest = rulesWithMaximum(original, 12L, 25);
        ManaService.tickPlayer(fixture.player, original);
        fixture.state.startRegenerationLockout(100L);
        ManaReservation claim = ManaService.reserveMana(fixture.player, 40);

        ManaService.applyRulesReload(fixture.player, original, first);
        ManaService.applyRulesReload(fixture.player, first, latest);

        assertEquals(60, fixture.state.getMaximumMana());
        assertEquals(100L, fixture.state.getRemainingLockoutTicks());
        assertEquals(original.getRevision(),
                ManaService.explainAcceptedProfile(fixture.player).getGameplayRulesRevision());
        assertTrue(ManaService.isReservationValid(fixture.player, claim));
        ManaService.releaseReservation(claim);

        ManaService.flushPendingProfileEvaluation(fixture.player, claim);
        assertEquals(25, fixture.state.getMaximumMana());
        assertEquals(25, fixture.state.getCurrentMana());
        assertEquals(12L, ManaService.explainAcceptedProfile(fixture.player).getGameplayRulesRevision());
        assertEquals(25, ManaService.explainAcceptedProfile(fixture.player).getFinalMaximumMana());
        assertEquals(0L, fixture.state.getRemainingLockoutTicks());
    }

    private static GameplayRules rulesWithMaximum(GameplayRules source, long revision, int maximum) {
        return new GameplayRules(revision, maximum, source.getBaseRegeneration(), 0,
                source.getCostFormula(), source.getSmallFlask(), source.getMediumFlask(),
                source.getLargeFlask(), Collections.emptyMap(), Collections.emptyList(),
                WizardryArmorProfile.DEFAULT);
    }

    @Test
    void maximumOnlyChangeMakesLeavingFullObservable() {
        PlayerFixture fixture = playerFixture();
        RecordingListener listener = register(new RecordingListener(fixture.player));
        GameplayRules previous = GameplayRules.initialDefaults();
        GameplayRules replacement = new GameplayRules(
                1L,
                100,
                previous.getBaseRegeneration(),
                previous.getRegenerationLockoutTicks(),
                previous.getCostFormula(),
                previous.getSmallFlask(),
                previous.getMediumFlask(),
                previous.getLargeFlask(),
                Collections.emptyMap(),
                Collections.emptyList(),
                WizardryArmorProfile.DEFAULT
        );

        ManaService.applyRulesReload(fixture.player, previous, replacement);

        assertEquals(1, listener.events.size());
        ManaStateChangeEvent event = listener.events.get(0);
        assertEquals(60, event.getOldState().getCurrentMana());
        assertEquals(60, event.getOldState().getMaximumMana());
        assertEquals(60, event.getNewState().getCurrentMana());
        assertEquals(100, event.getNewState().getMaximumMana());
        assertEquals(0, event.getActualManaDelta());
    }

    @Test
    void successfulContinuousFinishPublishesEndThenLockoutStart() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        RecordingListener listener = register(new RecordingListener(fixture.player));
        ResourceLocation spell = new ResourceLocation("ebwizardry", "flame_ray");

        ManaService.beginContinuousCast(fixture.player, EnumHand.MAIN_HAND, spell);
        fixture.state.markContinuousCastSuccessful(EnumHand.MAIN_HAND, spell);
        assertTrue(ManaService.finishContinuousCast(fixture.player));

        assertEquals(3, listener.events.size());
        assertEquals(ManaCauses.CONTINUOUS_START, listener.events.get(0).getCause());
        assertEquals(ManaCauses.CONTINUOUS_END, listener.events.get(1).getCause());
        assertEquals(ManaCauses.LOCKOUT_START, listener.events.get(2).getCause());
        assertFalse(listener.events.get(1).getNewState().isContinuousCastActive());
        assertEquals(0L, listener.events.get(1).getNewState().getRemainingLockoutTicks());
        assertEquals(40L, listener.events.get(2).getNewState().getRemainingLockoutTicks());
    }

    @Test
    void cloneResetPublishesTruthfulLifecycleTransition() {
        PlayerFixture original = playerFixture();
        PlayerFixture replacement = playerFixture();
        ResourceLocation spell = new ResourceLocation("ebwizardry", "flame_ray");
        original.state.tryConsume(10);
        original.state.startRegenerationLockout(20L);
        original.state.beginContinuousCast(EnumHand.MAIN_HAND, spell);
        RecordingListener listener = register(new RecordingListener(replacement.player));

        ManaService.copyForClone(replacement.player, original.state);

        assertEquals(1, listener.events.size());
        ManaStateChangeEvent event = listener.events.get(0);
        assertEquals(ManaCauses.LIFECYCLE, event.getCause());
        assertEquals(50, event.getOldState().getCurrentMana());
        assertEquals(20L, event.getOldState().getRemainingLockoutTicks());
        assertTrue(event.getOldState().isContinuousCastActive());
        assertEquals(50, event.getNewState().getCurrentMana());
        assertEquals(0L, event.getNewState().getRemainingLockoutTicks());
        assertFalse(event.getNewState().isContinuousCastActive());
    }

    @Test
    void clonePublishesLifecycleForLockoutOnlyResetAtFullMana() {
        PlayerFixture original = playerFixture();
        PlayerFixture replacement = playerFixture();
        original.state.startRegenerationLockout(20L);
        RecordingListener listener = register(new RecordingListener(replacement.player));

        ManaService.copyForClone(replacement.player, original.state);

        assertEquals(1, listener.events.size());
        ManaStateChangeEvent event = listener.events.get(0);
        assertEquals(ManaCauses.LIFECYCLE, event.getCause());
        assertEquals(20L, event.getOldState().getRemainingLockoutTicks());
        assertEquals(0L, event.getNewState().getRemainingLockoutTicks());
        assertFalse(event.getOldState().isRegenerationEligible());
        assertFalse(event.getNewState().isRegenerationEligible());
    }

    @Test
    void participatingListenerFailurePropagatesAfterFinalizedDebit() {
        PlayerFixture fixture = playerFixture();
        ThrowingListener listener = register(new ThrowingListener(fixture.player));
        ManaReservation claim = ManaService.reserveMana(fixture.player, 10);
        java.util.concurrent.atomic.AtomicBoolean finalized = new java.util.concurrent.atomic.AtomicBoolean();

        assertThrows(RuntimeException.class, () -> ManaService.commitParticipatingCastPayment(
                fixture.player, claim, 0, 0, () -> finalized.set(true)));

        assertTrue(finalized.get());
        assertEquals(50, fixture.state.getCurrentMana());
        assertEquals(1, listener.calls);
        assertFalse(ManaService.isReservationValid(fixture.player, claim));
    }

    @Test
    void listenerFailureDoesNotRollbackCommittedState() {
        PlayerFixture fixture = playerFixture();
        ThrowingListener listener = register(new ThrowingListener(fixture.player));

        ManaMutationResult result = new ManaService().consumeMana(
                fixture.player,
                10,
                new ResourceLocation("test", "listener_failure")
        );

        assertTrue(result.isSuccessful());
        assertEquals(50, fixture.state.getCurrentMana());
        assertEquals(1, listener.calls);
    }

    @Test
    void nestedListenerMutationsAreDepthFirstAndStopBeforeDepthSeventeen() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        LoopingListener listener = register(new LoopingListener(fixture.player, service));

        ManaMutationResult result = service.setCurrentMana(
                fixture.player,
                0,
                new ResourceLocation("test", "nested_start")
        );

        assertTrue(result.isSuccessful());
        assertEquals(ManaMutationCoordinator.MAX_DEPTH, listener.calls);
        assertEquals(1, fixture.state.getCurrentMana());
        assertEquals(0, ManaMutationCoordinator.currentDepth(fixture.player.getUniqueID()));
    }

    @Test
    void nestedLockoutRestartsStopAtTheSharedMutationDepthLimit() {
        PlayerFixture fixture = playerFixture();
        ManaService service = new ManaService();
        LoopingLockoutListener listener = register(
                new LoopingLockoutListener(fixture.player, service)
        );

        assertEquals(40L, service.startRegenerationLockout(
                fixture.player,
                new ResourceLocation("test", "nested_lockout_start")
        ));

        assertEquals(ManaMutationCoordinator.MAX_DEPTH, listener.calls);
        assertEquals(40L, fixture.state.getRemainingLockoutTicks());
        assertEquals(0, ManaMutationCoordinator.currentDepth(fixture.player.getUniqueID()));
    }

    private <T> T register(T listener) {
        MinecraftForge.EVENT_BUS.register(listener);
        registeredListeners.add(listener);
        return listener;
    }

    private static PlayerFixture playerFixture() {
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        ManaState state = new ManaState();
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
        return new PlayerFixture(player, state);
    }

    private static ResolvedManaProfile profileWithLockout(long lockoutTicks) {
        return new ResolvedManaProfile(
                60,
                2.4D,
                lockoutTicks / 20.0D,
                lockoutTicks,
                0.0D,
                Collections.emptyMap(),
                false,
                false
        );
    }

    private static final class PlayerFixture {

        private final EntityPlayer player;
        private final ManaState state;

        private PlayerFixture(EntityPlayer player, ManaState state) {
            this.player = player;
            this.state = state;
        }
    }

    public static final class RecordingListener {

        private final EntityPlayer player;
        private final List<ManaStateChangeEvent> events = new ArrayList<>();

        private RecordingListener(EntityPlayer player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onChange(ManaStateChangeEvent event) {
            if (event.getPlayer() == player) {
                events.add(event);
            }
        }
    }

    public static final class ThrowingListener {

        private final EntityPlayer player;
        private int calls;

        private ThrowingListener(EntityPlayer player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onChange(ManaStateChangeEvent event) {
            if (event.getPlayer() == player) {
                calls++;
                throw new IllegalStateException("deliberate listener failure");
            }
        }
    }

    public static final class ClearingLockoutListener {

        private final EntityPlayer player;
        private final ManaState state;

        private ClearingLockoutListener(EntityPlayer player, ManaState state) {
            this.player = player;
            this.state = state;
        }

        @SubscribeEvent
        public void onChange(ManaStateChangeEvent event) {
            if (event.getPlayer() == player) {
                state.startRegenerationLockout(0L);
            }
        }
    }

    public static final class LoopingListener {

        private final EntityPlayer player;
        private final ManaService service;
        private int calls;

        private LoopingListener(EntityPlayer player, ManaService service) {
            this.player = player;
            this.service = service;
        }

        @SubscribeEvent
        public void onChange(ManaStateChangeEvent event) {
            if (event.getPlayer() != player) {
                return;
            }
            calls++;
            ResourceLocation cause = new ResourceLocation("test", "nested_" + calls);
            if (event.getNewState().getCurrentMana() == 0) {
                service.restoreMana(player, 1, cause);
            } else {
                service.consumeMana(player, 1, cause);
            }
        }
    }

    public static final class LoopingLockoutListener {

        private final EntityPlayer player;
        private final ManaService service;
        private int calls;

        private LoopingLockoutListener(EntityPlayer player, ManaService service) {
            this.player = player;
            this.service = service;
        }

        @SubscribeEvent
        public void onChange(ManaStateChangeEvent event) {
            if (event.getPlayer() != player) {
                return;
            }
            calls++;
            service.startRegenerationLockout(
                    player,
                    new ResourceLocation("test", "nested_lockout_" + calls)
            );
        }
    }
}
