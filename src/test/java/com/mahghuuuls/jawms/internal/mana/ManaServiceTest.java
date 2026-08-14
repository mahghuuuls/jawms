package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        verify(player, never()).getCapability(any(), any());
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

        assertEquals(60, fixture.state.getCurrentMana());
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

        try (ManaEvaluationGuard.Scope ignored =
                     ManaEvaluationGuard.begin(fixture.player.getUniqueID())) {
            assertEquals(60, service.getCurrentMana(fixture.player));
            assertThrows(IllegalStateException.class,
                    () -> service.consumeMana(fixture.player, 1, cause));
        }

        assertEquals(60, fixture.state.getCurrentMana());
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
}
