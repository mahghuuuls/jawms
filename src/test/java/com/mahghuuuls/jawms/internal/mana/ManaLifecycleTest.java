package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.modifier.ManaTestEvaluations;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManaLifecycleTest {
    private static final ResourceLocation PROVIDER = new ResourceLocation("test", "lifecycle");
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

    @BeforeEach
    void registerProvider() throws Exception {
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
        ManaProviderRegistry registry = ManaProviderRegistry.instance();
        registry.remove(currentPlayer());
        Field registrations = ManaProviderRegistry.class.getDeclaredField("registrations");
        registrations.setAccessible(true);
        ((Map<?, ?>) registrations.get(registry)).remove(PROVIDER);
        ((Map<?, ?>) registrations.get(registry)).remove(REENTRANT);
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

    private static ManaState state(EntityPlayerMP player) {
        return player.getCapability(ManaCapability.INSTANCE, null);
    }
}
