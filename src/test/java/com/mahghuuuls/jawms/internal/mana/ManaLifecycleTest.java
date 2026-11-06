package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.modifier.ManaTestEvaluations;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManaLifecycleTest {
    private static final ResourceLocation PROVIDER = new ResourceLocation("test", "lifecycle");
    private static final ResourceLocation PEER = new ResourceLocation("test", "lifecycle_peer");
    private static final ResourceLocation REENTRANT = new ResourceLocation("test", "lifecycle_reentrant");
    private static final ManaContribution BONUS = ManaContribution.builder().flatMaximumMana(20).build();
    private static final GameplayRules RULES = new GameplayRules(1, 100, 0, 0L, "base",
            20, 50, 100, Collections.emptyMap(), Collections.emptyList(), WizardryArmorProfile.DEFAULT);

    private final MinecraftServer server = mock(MinecraftServer.class);
    private final PlayerList players = mock(PlayerList.class);
    private final WorldServer overworld = mock(WorldServer.class);
    private final WorldServer nether = mock(WorldServer.class);
    private final UUID playerId = UUID.randomUUID();
    private final ManaService mana = new ManaService();
    private ManaReadinessProvider provider;
    private Field currentRules;
    private GameplayRules previousRules;

    @BeforeEach
    void registerProvider() throws Exception {
        currentRules = GameplayRulesManager.class.getDeclaredField("current");
        currentRules.setAccessible(true);
        previousRules = GameplayRulesManager.current();
        currentRules.set(null, RULES);
        provider = ManaApi.registerReadinessProvider(PROVIDER);
        when(server.getPlayerList()).thenReturn(players);
        when(server.getWorld(0)).thenReturn(overworld);
        when(server.getWorld(-1)).thenReturn(nether);
        when(overworld.getMinecraftServer()).thenReturn(server);
        when(nether.getMinecraftServer()).thenReturn(server);
        setDimension(overworld, 0);
        setDimension(nether, -1);
    }

    @AfterEach
    void unregisterProvider() throws Exception {
        currentRules.set(null, previousRules);
        ManaProviderRegistry registry = ManaProviderRegistry.instance();
        registry.remove(currentPlayer());
        Field registrations = ManaProviderRegistry.class.getDeclaredField("registrations");
        registrations.setAccessible(true);
        ((Map<?, ?>) registrations.get(registry)).remove(PROVIDER);
        ((Map<?, ?>) registrations.get(registry)).remove(PEER);
        ((Map<?, ?>) registrations.get(registry)).remove(REENTRANT);
    }

    @Test
    void withholdingReplacementLeavesTheRecordedGroupEligible() throws Exception {
        ManaReadinessProvider peer = ManaApi.registerReadinessProvider(PEER);
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration bonusToken = token(player);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
        assertEquals(110, savedMana(player));

        assertEquals(INITIAL_RECORDED, peer.completeInitial(player, peer.getGeneration(player).get(),
                ManaContribution.EMPTY).getStatus());
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(100, savedMana(player));
        assertEquals(100, mana.getMaximumMana(player));

        assertEquals(UPDATE_RECORDED, provider.update(player, bonusToken, 1, BONUS).getStatus());
        assertEquals(120, mana.getMaximumMana(player));
        assertEquals(100, savedMana(player));
    }

    @Test
    void acceptedExplanationIncludesVerifiedEmptyReadinessGroupsAcrossUpdates() throws Exception {
        ManaReadinessProvider peer = ManaApi.registerReadinessProvider(PEER);
        AtomicInteger providerCalls = new AtomicInteger();
        ManaProviderRegistry.instance().registerContextual(REENTRANT, player -> {
            providerCalls.incrementAndGet();
            return ManaContribution.EMPTY;
        });
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration bonusToken = token(player);
        assertEquals(INITIAL_RECORDED, peer.completeInitial(player, peer.getGeneration(player).get(),
                ManaContribution.EMPTY).getStatus());
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(100, mana.getCurrentMana(player));
        assertEquals(100, mana.getMaximumMana(player));

        ManaProfileExplanation initial = readExplanationWithoutMutation(player, providerCalls);
        assertEquals(100, initial.getFinalMaximumMana());
        assertEmptySource(initial, PROVIDER);
        assertEmptySource(initial, PEER);

        ManaContributionPublicationResult applied = provider.update(player, bonusToken, 1, BONUS);
        assertEquals(UPDATE_RECORDED, applied.getStatus());
        assertEquals(2L, applied.getRevision().getAsLong());
        ManaProfileExplanation atUpdate = readExplanationWithoutMutation(player, providerCalls);
        assertEquals(mana.getMaximumMana(player), atUpdate.getFinalMaximumMana());
        assertEmptySource(atUpdate, PEER);

        when(overworld.getTotalWorldTime()).thenReturn(1L);
        ManaService.tickPlayer(player, RULES);
        ManaProfileExplanation afterUpdate = readExplanationWithoutMutation(player, providerCalls);
        assertEquals(120, mana.getMaximumMana(player));
        assertEquals(120, afterUpdate.getFinalMaximumMana());
        assertEmptySource(afterUpdate, PEER);
        ManaContributionExplanation active = explanationSource(afterUpdate, PROVIDER);
        assertEquals(ManaContributionDisposition.APPLIED, active.getDisposition());
        assertEquals(ManaContributionFailure.NONE, active.getFailure());
        assertEquals(20, active.getAcceptedContribution().getFlatMaximumMana());

        mana.setCurrentMana(player, 110, ManaCauses.COMMAND);
        assertEquals(110, mana.getCurrentMana(player));
        ManaContributionPublicationResult removed = provider.update(player, bonusToken,
                applied.getRevision().getAsLong(), ManaContribution.EMPTY);
        assertEquals(UPDATE_RECORDED, removed.getStatus());
        assertEquals(3L, removed.getRevision().getAsLong());
        when(overworld.getTotalWorldTime()).thenReturn(2L);
        ManaService.tickPlayer(player, RULES);

        ManaProfileExplanation afterRemoval = readExplanationWithoutMutation(player, providerCalls);
        assertEquals(100, mana.getCurrentMana(player));
        assertEquals(100, mana.getMaximumMana(player));
        assertEquals(100, afterRemoval.getFinalMaximumMana());
        assertEmptySource(afterRemoval, PROVIDER);
        assertEmptySource(afterRemoval, PEER);
    }

    @Test
    @EnabledIfSystemProperty(named = "jawms.api18FrozenConsumer", matches = "true")
    void frozenApi18ConsumerPublishesThroughTheCandidate() throws Exception {
        ManaReadinessProvider peer = ManaApi.registerReadinessProvider(PEER);
        EntityPlayerMP player = pendingPlayer(BONUS);
        Class<?> consumer = Class.forName("example.jawmsapi18.ReadinessConsumer");
        assertEquals(2L, consumer.getMethod("publish", ManaReadinessProvider.class, EntityPlayer.class)
                .invoke(null, peer, player));
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(110, savedMana(player));
        assertEquals(120, mana.getMaximumMana(player));
    }

    @Test
    void suspensionPreservesTheOriginalDeadlineAndSavedManaUntilVerifiedRecovery() throws Exception {
        ManaReadinessProvider peer = ManaApi.registerReadinessProvider(PEER);
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        long beforeSuspension = managedSequence(player);
        assertEquals(SUSPENDED, provider.suspend(player, token, 1).getStatus());
        assertEquals(beforeSuspension, managedSequence(player));
        assertEquals(INITIAL_RECORDED, peer.completeInitial(player, peer.getGeneration(player).get(),
                ManaContribution.EMPTY).getStatus());

        when(server.getTickCounter()).thenReturn(99);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
        assertEquals(beforeSuspension, managedSequence(player));
        when(server.getTickCounter()).thenReturn(100);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
        assertSame(token, token(player));
        assertEquals(beforeSuspension + 1, managedSequence(player));

        when(server.getTickCounter()).thenReturn(101);
        ManaService.tickPlayer(player, RULES);
        assertEquals(beforeSuspension + 1, managedSequence(player));

        assertEquals(UPDATE_RECORDED, provider.update(player, token, 2, BONUS).getStatus());
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        long acceptedSequence = managedSequence(player);
        assertEquals(beforeSuspension + 2, acceptedSequence);
        assertEquals(ALREADY_ACCEPTED, provider.suspend(player, token, 3).getStatus());
        assertEquals(acceptedSequence, managedSequence(player));
        assertEquals(110, savedMana(player));
        assertEquals(UPDATE_RECORDED, provider.update(player, token, 3, ManaContribution.EMPTY).getStatus());
        assertEquals(100, mana.getMaximumMana(player));
        assertEquals(100, savedMana(player));
    }

    @Test
    void verifiedEmptyCanRecoverBeforeTheDeadlineAndClampOnce() throws Exception {
        EntityPlayerMP player = pendingPlayer(BONUS);
        ManaProfileGeneration token = token(player);
        assertThrows(IllegalArgumentException.class, () -> provider.suspend(player, token, 0));
        assertThrows(IllegalStateException.class, () -> provider.suspend(mock(EntityPlayer.class), token, 1));
        when(overworld.isCallingFromMinecraftThread()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> provider.suspend(player, token, 1));
        when(overworld.isCallingFromMinecraftThread()).thenReturn(true);
        assertEquals(SUSPENDED, provider.suspend(player, token, 1).getStatus());
        assertEquals(110, savedMana(player));
        assertEquals(UPDATE_RECORDED, provider.update(player, token, 2, ManaContribution.EMPTY).getStatus());
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(100, mana.getMaximumMana(player));
        assertEquals(100, savedMana(player));
    }

    @Test
    void suspensionDuringEvaluationAtDeadlineStillTimesOutAtThatTick() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        ManaProviderRegistry.instance().registerContextual(REENTRANT, evaluated -> {
            assertEquals(SUSPENDED, provider.suspend(evaluated, token, 1).getStatus());
            assertEquals(BUSY, provider.update(evaluated, token, 2, BONUS).getStatus());
            return ManaContribution.EMPTY;
        });
        when(server.getTickCounter()).thenReturn(100);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
    }

    @Test
    void restoredCompletenessCannotCommitAnEvaluationOfTheOldGroup() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        AtomicInteger calls = new AtomicInteger();
        ManaProviderRegistry.instance().registerContextual(REENTRANT, evaluated -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(SUSPENDED, provider.suspend(evaluated, token, 1).getStatus());
                // Isolate the commit check without permitting reentrant recovery through the public API.
                assertEquals(UPDATE_RECORDED, state(player).readiness.publish(PROVIDER, overworld, token, BONUS,
                        false, 2).getStatus());
            }
            return ManaContribution.EMPTY;
        });
        ManaService.tickPlayer(player, RULES);
        assertEquals(1, calls.get());
        assertTrue(state(player).readiness.missing().isEmpty());
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));

        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
    }

    @Test
    void reloadedRulesInvalidateAnInFlightFirstAcceptance() throws Exception {
        EntityPlayerMP player = pendingPlayer(BONUS);
        GameplayRules replacement = new GameplayRules(2, 90, 0, 0L, "base",
                20, 50, 100, Collections.emptyMap(), Collections.emptyList(), WizardryArmorProfile.DEFAULT);
        ManaProviderRegistry.instance().registerContextual(REENTRANT, evaluated -> {
            assertDoesNotThrow(() -> currentRules.set(null, replacement));
            return ManaContribution.EMPTY;
        });
        ManaService.tickPlayer(player, RULES);
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));

        ManaService.tickPlayer(player, replacement);
        assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
        assertEquals(110, mana.getMaximumMana(player));
    }

    @Test
    void suspensionSurvivesCancelledAndThrowingDeathCallbacks() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        assertTrue(ManaLifecycle.checkDeath(player, () -> {
            assertEquals(SUSPENDED, provider.suspend(player, token, 1).getStatus());
            return true;
        }));
        assertEquals(UPDATE_RECORDED, provider.update(player, token, 2, ManaContribution.EMPTY).getStatus());
        RuntimeException failure = new IllegalStateException("death listener fault");
        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.checkDeath(player, () -> {
            assertEquals(SUSPENDED, provider.suspend(player, token, 3).getStatus());
            throw failure;
        })));
        ManaService.tickPlayer(player, RULES);
        assertSame(token, token(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
    }

    @Test
    void suspensionSurvivesNestedPreMutationRollbackOfTheExactSource() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        RuntimeException failure = new IllegalStateException("pre-mutation transfer fault");
        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.transfer(player,
                () -> ManaLifecycle.transfer(player, () -> {
                    assertFalse(provider.getGeneration(player).isPresent());
                    assertEquals(SUSPENDED, provider.suspend(player, token, 1).getStatus());
                    assertEquals(STALE_GENERATION, provider.update(player, token, 2, BONUS).getStatus());
                    throw failure;
                }))));
        ManaService.tickPlayer(player, RULES);
        assertSame(token, token(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
    }

    @Test
    void brokenNestedRollbackCannotSuspendAnOlderSource() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProfileGeneration token = token(player);
        ManaLifecycle.transfer(player, () -> ManaLifecycle.transfer(player, () -> {
            ManaLifecycle.transferMutated(player);
            assertEquals(STALE_GENERATION, provider.suspend(player, token, 1).getStatus());
        }));
        assertEquals(110, savedMana(player));
    }

    @Test
    void cancelledDeathAndThrowingCallbackLeaveTheSourceProfileAndTokenUsable() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        ManaProfileGeneration token = token(player);
        ManaReservation claim = ManaService.reserveMana(player, 10);

        assertTrue(ManaLifecycle.checkDeath(player, () -> {
            assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
            assertFalse(provider.getGeneration(player).isPresent());
            assertEquals(BUSY, provider.update(player, token, 1, ManaContribution.EMPTY).getStatus());
            assertEquals(ALREADY_ACCEPTED, provider.suspend(player, token, 1).getStatus());
            assertFalse(ManaService.isReservationValid(player, claim));
            assertEquals(110, savedMana(player));
            return true;
        }));
        RuntimeException thrown = new IllegalStateException("death listener fault");
        assertSame(thrown, assertThrows(RuntimeException.class,
                () -> ManaLifecycle.checkDeath(player, () -> { throw thrown; })));

        assertSame(token, token(player));
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        assertTrue(ManaService.isReservationValid(player, claim));
        ManaService.releaseReservation(claim);
    }

    @Test
    void deathAndSameDimensionReplacementRejectOldWorkAndRetainTheContinuingBonus() throws Exception {
        verifyReplacement(false, BONUS, 110, 120);
    }

    @Test
    void endReturnAcceptsAnExplicitEmptyReplacementOnceAndClampsTheRetainedMana() throws Exception {
        verifyReplacement(true, ManaContribution.EMPTY, 100, 100);
    }

    private void verifyReplacement(boolean endReturn, ManaContribution group, int expected, int maximum) throws Exception {
        EntityPlayerMP old = acceptedPlayer();
        ManaProfileGeneration oldToken = token(old);
        ManaReservation oldClaim = ManaService.reserveMana(old, 10);
        if (endReturn) ManaLifecycle.retire(old);
        else assertFalse(ManaLifecycle.checkDeath(old, () -> false));

        assertEquals(110, savedMana(old));
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(old));
        assertEquals(STALE_GENERATION, provider.update(old, oldToken, 1, ManaContribution.EMPTY).getStatus());
        assertFalse(ManaService.isReservationValid(old, oldClaim));

        EntityPlayerMP replacement = player(overworld, 0);
        assertEquals(old.getEntityId(), replacement.getEntityId());
        replacement.connection = old.connection;
        ManaLifecycle.retire(old);
        ManaService.copyForClone(replacement, state(old));
        assertEquals(110, savedMana(replacement));
        assertFalse(state(replacement).hasAcceptedProfile());

        when(players.getPlayerByUUID(playerId)).thenReturn(replacement);
        ManaLifecycle.bindReplacement(replacement, endReturn);
        assertSame(old, replacement.connection.player);
        assertEquals(INITIAL_RECORDED, provider.completeInitial(replacement, token(replacement), group).getStatus());
        ManaLifecycle.finishReplacement(replacement);
        ManaService.tickPlayer(replacement, RULES);
        assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(replacement));
        assertEquals(110, savedMana(replacement));

        replacement.connection.player = replacement;
        ManaService.tickPlayer(replacement, RULES);
        assertEquals(expected, mana.getCurrentMana(replacement));
        assertEquals(maximum, mana.getMaximumMana(replacement));
        assertEquals(endReturn ? ManaReadiness.Lifecycle.END_RETURN : ManaReadiness.Lifecycle.RESPAWN,
                state(replacement).readiness.lifecycle);
        long revision = state(replacement).getRevision();
        ManaService.tickPlayer(replacement, RULES);
        assertEquals(revision, state(replacement).getRevision());
        ManaService.tickPlayer(old, RULES);
        assertEquals(110, savedMana(old));
    }

    @Test
    void preMutationFailureRestoresTheVerifiedSourceAndPreservesTheOriginalException() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        ManaProfileGeneration original = token(player);
        ManaReservation claim = ManaService.reserveMana(player, 5);
        RuntimeException failure = new IllegalStateException("before first write");

        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.transfer(player, () -> {
            assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
            assertFalse(provider.getGeneration(player).isPresent());
            throw failure;
        })));
        assertSame(original, token(player));
        assertEquals(110, mana.getCurrentMana(player));
        assertTrue(ManaService.isReservationValid(player, claim));
        ManaService.releaseReservation(claim);
    }

    @Test
    void cancelledForgeTravelKeepsTheAcceptedSourceProfile() throws Exception {
        Bootstrap.register();
        EntityPlayerMP player = acceptedPlayer();
        ManaProfileGeneration source = token(player);
        ManaReadiness.Lifecycle lifecycle = state(player).readiness.lifecycle;
        long revision = state(player).getRevision();
        ManaReservation claim = ManaService.reserveMana(player, 10);
        ITeleporter teleporter = mock(ITeleporter.class);
        Field playerServer = EntityPlayerMP.class.getField("server");
        playerServer.setAccessible(true);
        playerServer.set(player, server);
        doCallRealMethod().when(player).changeDimension(-1, teleporter);

        // Plain unit tests lack Forge's event transformer. Install a scoped listener
        // and set the cancellation bit that ForgeHooks reads in the real travel path.
        Field busId = EventBus.class.getDeclaredField("busID");
        busId.setAccessible(true);
        int forgeBus = busId.getInt(MinecraftForge.EVENT_BUS);
        Field cancelled = Event.class.getDeclaredField("isCanceled");
        cancelled.setAccessible(true);
        ListenerList listeners = new EntityTravelToDimensionEvent(player, -1).getListenerList();
        AtomicInteger calls = new AtomicInteger();
        IEventListener cancelTravel = event -> {
            if (!(event instanceof EntityTravelToDimensionEvent)) return;
            EntityTravelToDimensionEvent travel = (EntityTravelToDimensionEvent) event;
            if (travel.getEntity() != player || travel.getDimension() != -1) return;
            calls.incrementAndGet();
            assertSame(source, token(player));
            assertEquals(ManaProfileReadiness.READY, ManaApi.getProfileReadiness(player));
            assertDoesNotThrow(() -> cancelled.setBoolean(travel, true));
        };
        listeners.register(forgeBus, EventPriority.NORMAL, cancelTravel);
        try {
            assertSame(player, player.changeDimension(-1, teleporter));
        } finally {
            listeners.unregister(forgeBus, cancelTravel);
        }

        assertEquals(1, calls.get());
        verify(players, never()).transferPlayerToDimension(player, -1, teleporter);
        assertSame(overworld, player.world);
        assertEquals(0, player.dimension);
        ManaService.tickPlayer(player, RULES);
        assertSame(source, token(player));
        assertEquals(lifecycle, state(player).readiness.lifecycle);
        assertEquals(revision, state(player).getRevision());
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        assertEquals(110, savedMana(player));
        assertTrue(ManaService.isReservationValid(player, claim));
        ManaService.releaseReservation(claim);
    }

    @Test
    void directTransferWaitsForDestinationInstallationAndSuccessfulReturn() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        ManaProfileGeneration source = token(player);

        ManaLifecycle.transfer(player, () -> {
            player.dimension = -1;
            ManaLifecycle.transferMutated(player);
            ManaLifecycle.bindTransferDestination(player);
            assertFalse(provider.getGeneration(player).isPresent());
            install(player, nether, -1);
            ManaLifecycle.bindTransferDestination(player);
            assertEquals(STALE_GENERATION, provider.update(player, source, 1, ManaContribution.EMPTY).getStatus());
            assertEquals(INITIAL_RECORDED, provider.completeInitial(player, token(player), BONUS).getStatus());
            ManaService.tickPlayer(player, RULES);
            assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
            assertEquals(110, savedMana(player));
        });

        ManaService.tickPlayer(player, RULES);
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        assertEquals(ManaReadiness.Lifecycle.DIMENSION_TRANSFER, state(player).readiness.lifecycle);
    }

    @Test
    void failedTransferWithSameDimensionWriteDiscardsTheAttemptAndRequiresFreshCompletion() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        ManaProfileGeneration source = token(player);
        ManaProfileGeneration[] failedToken = new ManaProfileGeneration[1];
        RuntimeException failure = new IllegalStateException("after same-dimension write");

        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.transfer(player, () -> {
            ManaLifecycle.transferMutated(player);
            ManaLifecycle.bindTransferDestination(player);
            failedToken[0] = token(player);
            assertEquals(INITIAL_RECORDED,
                    provider.completeInitial(player, failedToken[0], ManaContribution.EMPTY).getStatus());
            when(overworld.getEntityByID(player.getEntityId())).thenReturn(null);
            throw failure;
        })));
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertEquals(110, savedMana(player));
        assertFalse(provider.getGeneration(player).isPresent());
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));

        install(player, overworld, 0);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
        assertEquals(STALE_GENERATION, provider.completeInitial(player, source, ManaContribution.EMPTY).getStatus());
        assertEquals(STALE_GENERATION, provider.completeInitial(player, failedToken[0], ManaContribution.EMPTY).getStatus());
        assertEquals(110, savedMana(player));
        assertEquals(INITIAL_RECORDED, provider.completeInitial(player, token(player), BONUS).getStatus());
        ManaService.tickPlayer(player, RULES);
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        assertEquals(ManaReadiness.Lifecycle.TRANSFER_RECOVERY, state(player).readiness.lifecycle);
    }

    @Test
    void shutdownSaveAndLaterLogoutBothSerializeOnlyTheSameIntrinsicBalance() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        NBTTagCompound before = state(player).serializeIntrinsicState();
        ManaService.retireLogout(player);
        NBTTagCompound after = state(player).serializeIntrinsicState();
        assertEquals(Collections.singleton("currentMana"), before.getKeySet());
        assertEquals(before, after);
        assertEquals(110, after.getInteger("currentMana"));
    }

    @Test
    void nativeAdaptersPassThroughWhenNoReadinessStateExists() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        assertTrue(ManaLifecycle.checkDeath(player, () -> true));
        RuntimeException failure = new IllegalStateException("vanilla transfer failure");
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> ManaLifecycle.transfer(player, () -> { throw failure; })));
        ManaLifecycle.retire(player);
    }

    @Test
    void completedEmptyProfileCannotAcceptAfterConnectionClosesBeforeLogout() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        when(player.connection.getNetworkManager().isChannelOpen()).thenReturn(false);
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.PENDING, ManaApi.getProfileReadiness(player));
        assertFalse(provider.getGeneration(player).isPresent());
        assertEquals(110, savedMana(player));
        ManaService.retireLogout(player);
        assertEquals(110, savedMana(player));
    }

    @Test
    void retirementDuringEvaluationCannotAcceptAnObsoleteProfile() throws Exception {
        EntityPlayerMP player = pendingPlayer(ManaContribution.EMPTY);
        ManaProviderRegistry.instance().registerContextual(REENTRANT, evaluated -> {
            ManaLifecycle.retire((EntityPlayerMP) evaluated);
            return ManaContribution.EMPTY;
        });
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
    }

    @Test
    void retirementDuringAnOrdinaryManagedTickDiscardsTheEvaluatedProfile() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        ManaProviderRegistry.instance().registerContextual(REENTRANT, evaluated -> {
            ManaLifecycle.retire((EntityPlayerMP) evaluated);
            return ManaContribution.EMPTY;
        });
        ManaService.tickPlayer(player, RULES);
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertFalse(state(player).hasAcceptedProfile());
        assertEquals(110, savedMana(player));
    }

    @Test
    void retirementInsidePreMutationTransferPreservesTheAcceptedBalance() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        RuntimeException failure = new IllegalStateException("source retired before first write");
        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.transfer(player, () -> {
            ManaLifecycle.retire(player);
            throw failure;
        })));
        assertEquals(ManaProfileReadiness.UNAVAILABLE, ManaApi.getProfileReadiness(player));
        assertEquals(110, savedMana(player));
    }

    @Test
    void cancelledDeathKeepsTheSameLifecycleButHonorsPolicyRevocation() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        UUID generation = state(player).readiness.identity;
        ManaProfileGeneration previous = token(player);
        ManaReservation claim = ManaService.reserveMana(player, 20);
        state(player).applyProfileEvaluation(ManaTestEvaluations.withMaximum(5));
        assertTrue(state(player).hasPendingProfileEvaluation());
        assertTrue(ManaLifecycle.checkDeath(player, () -> {
            reloadProviderPolicy(player);
            return true;
        }));
        assertEquals(generation, state(player).readiness.identity);
        assertFalse(state(player).hasPendingProfileEvaluation());
        ManaService.releaseReservation(claim);
        ManaService.flushPendingProfileEvaluation(player, claim);
        assertEquals(110, savedMana(player));
        assertNotSame(previous, token(player));
        assertEquals(STALE_GENERATION, provider.update(player, previous, 1, ManaContribution.EMPTY).getStatus());
    }

    @Test
    void preMutationRollbackPreservesPolicyChangesMadeDuringTheAttempt() throws Exception {
        EntityPlayerMP player = acceptedPlayer();
        UUID generation = state(player).readiness.identity;
        ManaProfileGeneration previous = token(player);
        RuntimeException failure = new IllegalStateException("after policy change before first write");
        assertSame(failure, assertThrows(RuntimeException.class, () -> ManaLifecycle.transfer(player, () -> {
            reloadProviderPolicy(player);
            throw failure;
        })));
        assertEquals(generation, state(player).readiness.identity);
        assertNotSame(previous, token(player));
        assertEquals(STALE_GENERATION, provider.update(player, previous, 1, ManaContribution.EMPTY).getStatus());
    }

    private static void reloadProviderPolicy(EntityPlayerMP player) {
        GameplayRules disabled = new GameplayRules(2, 100, 0, 0L, "base",
                20, 50, 100, Collections.emptyMap(), Collections.emptyList(), WizardryArmorProfile.DEFAULT,
                Collections.singleton(PROVIDER));
        ManaService.applyRulesReload(player, RULES, disabled);
        ManaService.applyRulesReload(player, disabled, RULES);
    }

    private ManaProfileExplanation readExplanationWithoutMutation(EntityPlayerMP player,
            AtomicInteger providerCalls) throws Exception {
        ManaPublicState before = mana.getState(player);
        ManaProfileReadiness readiness = ManaApi.getProfileReadiness(player);
        long publications = state(player).readiness.publicationVersion();
        long sequence = managedSequence(player);
        int calls = providerCalls.get();

        ManaProfileExplanation explanation = ManaApi.explainManaProfile(player);
        ManaProfileExplanation repeated = ManaApi.explainManaProfile(player);

        assertEquals(explanation.getEvaluatedWorldTick(), repeated.getEvaluatedWorldTick());
        assertEquals(explanation.getGameplayRulesRevision(), repeated.getGameplayRulesRevision());
        assertEquals(explanation.getContributions(), repeated.getContributions());
        assertEquals(before, mana.getState(player));
        assertEquals(readiness, ManaApi.getProfileReadiness(player));
        assertEquals(publications, state(player).readiness.publicationVersion());
        assertEquals(sequence, managedSequence(player));
        assertEquals(calls, providerCalls.get());
        return explanation;
    }

    private static void assertEmptySource(ManaProfileExplanation explanation, ResourceLocation sourceId) {
        ManaContributionExplanation source = explanationSource(explanation, sourceId);
        assertEquals(ManaContributionDisposition.EMPTY, source.getDisposition());
        assertEquals(ManaContributionFailure.NONE, source.getFailure());
        assertFalse(source.hasAcceptedContribution());
    }

    private static ManaContributionExplanation explanationSource(ManaProfileExplanation explanation,
            ResourceLocation sourceId) {
        return explanation.getContributions().stream()
                .filter(source -> source.getSourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing explanation for " + sourceId));
    }

    private static void setDimension(WorldServer world, int dimension) throws Exception {
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(dimension);
        Field field = World.class.getField("provider");
        field.setAccessible(true);
        field.set(world, provider);
    }

    private EntityPlayerMP acceptedPlayer() throws Exception {
        EntityPlayerMP player = pendingPlayer(BONUS);
        ManaService.tickPlayer(player, RULES);
        assertEquals(110, mana.getCurrentMana(player));
        assertEquals(120, mana.getMaximumMana(player));
        return player;
    }

    private EntityPlayerMP pendingPlayer(ManaContribution group) throws Exception {
        EntityPlayerMP player = player(overworld, 0);
        ManaTestConnection.open(server, player);
        when(players.getPlayerByUUID(playerId)).thenReturn(player);
        ManaService.bindLogin(player);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("currentMana", 110);
        state(player).deserializeIntrinsicState(saved);
        assertEquals(INITIAL_RECORDED, provider.completeInitial(player, token(player), group).getStatus());
        ManaService.finishLogin(player);
        return player;
    }

    private EntityPlayerMP player(WorldServer world, int dimension) throws Exception {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        ManaState state = new ManaState();
        when(player.getServer()).thenReturn(server);
        when(player.getUniqueID()).thenReturn(playerId);
        when(player.getEntityId()).thenReturn(17);
        when(player.isEntityAlive()).thenReturn(true);
        when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
        Field manager = EntityPlayerMP.class.getField("interactionManager");
        manager.setAccessible(true);
        manager.set(player, new PlayerInteractionManager(world));
        install(player, world, dimension);
        return player;
    }

    private static void install(EntityPlayerMP player, WorldServer world, int dimension) {
        player.world = world;
        player.dimension = dimension;
        player.interactionManager.world = world;
        when(player.getServerWorld()).thenReturn(world);
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(world.getEntityByID(player.getEntityId())).thenReturn(player);
    }

    private EntityPlayerMP currentPlayer() {
        return players.getPlayerByUUID(playerId);
    }

    private ManaProfileGeneration token(EntityPlayerMP player) {
        return provider.getGeneration(player).get();
    }

    private static int savedMana(EntityPlayerMP player) {
        return state(player).serializeIntrinsicState().getInteger("currentMana");
    }

    private static long managedSequence(EntityPlayerMP player) throws Exception {
        Field field = ManaNetwork.class.getDeclaredField("MANAGED_SEQUENCES");
        field.setAccessible(true);
        Long sequence = (Long) ((Map<?, ?>) field.get(null)).get(player.connection);
        return sequence == null ? 0L : sequence;
    }

    private static ManaState state(EntityPlayerMP player) {
        return player.getCapability(ManaCapability.INSTANCE, null);
    }
}
