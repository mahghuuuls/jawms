package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseService;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.Set;
import java.util.function.BooleanSupplier;

import static com.mahghuuuls.jawms.internal.mana.ManaReadiness.Lifecycle.*;

/** Translates native lifecycle boundaries into the context consumed by ManaService. */
public final class ManaLifecycle {
    private ManaLifecycle() {
    }

    public static boolean checkDeath(EntityPlayerMP player, BooleanSupplier forgeDeath) {
        ManaState state = managedState(player);
        if (state == null || state.readiness.isRetired()) {
            return forgeDeath.getAsBoolean();
        }
        boolean cancelled;
        boolean previousGuard = state.checkingDeath;
        state.checkingDeath = true;
        try {
            cancelled = forgeDeath.getAsBoolean();
        } finally {
            state.checkingDeath = previousGuard;
        }
        if (!cancelled) retire(player);
        return cancelled;
    }

    public static void retire(EntityPlayerMP player) {
        ManaState state = managedState(player);
        if (state == null) return;
        state.recoveringTransfer = false;
        if (state.readiness.isRetired()) return;
        state.retireReadiness();
        clearContexts(player);
    }

    static void armReplacement(EntityPlayerMP player, ManaState state) {
        state.readiness = newGeneration(player, GameplayRulesManager.current(), RESPAWN);
    }

    public static void bindReplacement(EntityPlayerMP player, boolean endReturn) {
        ManaState state = managedState(player);
        if (state == null) return;
        ManaReadiness readiness = state.readiness;
        readiness.lifecycle = endReturn ? END_RETURN : RESPAWN;
        bindWorld(readiness, player.getServerWorld());
    }

    public static void finishReplacement(EntityPlayerMP player) {
        ManaState state = managedState(player);
        if (state != null) state.readiness.complete = true;
    }

    public static void transfer(EntityPlayerMP player, Runnable vanillaTransfer) {
        ManaState state = managedState(player);
        if (state == null) {
            vanillaTransfer.run();
            return;
        }
        Transfer frame = new Transfer(player, state);
        state.transfer = frame;
        state.readiness = frame.destination;
        state.recoveringTransfer = false;
        try {
            vanillaTransfer.run();
            if (state.readiness == frame.destination) frame.destination.complete = true;
        } catch (RuntimeException | Error failure) {
            try {
                failTransfer(player, state, frame);
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } finally {
            state.transfer = frame.previous;
        }
    }

    /** Called immediately after vanilla writes the destination dimension. */
    public static void transferMutated(EntityPlayerMP player) {
        ManaState state = managedState(player);
        if (state == null) return;
        Transfer frame = state.transfer;
        if (frame == null || state.readiness != frame.destination) return;
        frame.mutated = true;
        retireTransferSource(player, state, frame);
    }

    /** The Forge notification follows installation of the destination world. */
    public static void bindTransferDestination(EntityPlayerMP player) {
        ManaState state = managedState(player);
        if (state == null) return;
        Transfer frame = state.transfer;
        if (frame != null && state.readiness == frame.destination && frame.mutated
                && isInstalledInWorld(player, player.getServerWorld())) {
            bindWorld(frame.destination, player.getServerWorld());
        }
    }

    private static void failTransfer(EntityPlayerMP player, ManaState state, Transfer frame) {
        if (state.readiness != frame.destination || frame.destination.isRetired()) return;
        frame.destination.retire();
        if (!frame.mutated && player.dimension == frame.sourceDimension
                && player.connection == frame.sourceConnection
                && isInstalledInWorld(player, frame.sourceWorld)) {
            state.readiness = frame.source;
            state.recoveringTransfer = frame.sourceRecovering;
            return;
        }
        state.recoveringTransfer = true;
        if (!frame.mutated) retireTransferSource(player, state, frame);
        JustAnotherWizardryManaSystemMod.LOGGER.warn(
                "MANA_PROFILE_TRANSFER_UNAVAILABLE player={} generation={} mutated={}",
                player.getUniqueID(), frame.destination.identity, frame.mutated);
        ManaNetwork.synchronize(player, state);
    }

    private static void retireTransferSource(EntityPlayerMP player, ManaState state, Transfer frame) {
        state.retainIntrinsicMana(frame.source);
        frame.source.retire();
        state.clearTransientLifecycleState();
        clearContexts(player);
    }

    static void applyTransferSourcePolicy(ManaState state, Set<ResourceLocation> eligible) {
        for (Transfer frame = state.transfer; frame != null && !frame.mutated; frame = frame.previous) {
            frame.source.applyPolicy(eligible);
        }
    }

    static void recoverTransfer(EntityPlayerMP player, ManaState state, GameplayRules rules) {
        WorldServer world = player.getServerWorld();
        if (!isInstalledInWorld(player, world)) return;
        ManaReadiness recovery = newGeneration(player, rules, TRANSFER_RECOVERY);
        bindWorld(recovery, world);
        recovery.complete = true;
        state.readiness = recovery;
        state.recoveringTransfer = false;
        ManaNetwork.synchronize(player, state);
    }

    static WorldServer publicationWorld(EntityPlayerMP player, ManaReadiness readiness) {
        if (!readiness.bound || readiness.isRetired() || !hasOpenConnection(player)) return null;
        WorldServer world = player.getServerWorld();
        if (world == null || (readiness.world != null && readiness.world != world)) return null;
        if (isReconcilingReplacement(player, readiness)) return world;
        return isCurrent(player, world) ? world : null;
    }

    static boolean isReconcilingReplacement(EntityPlayerMP player, ManaReadiness readiness) {
        return readiness.bound && !readiness.complete
                && (readiness.lifecycle == RESPAWN || readiness.lifecycle == END_RETURN)
                && hasOpenConnection(player) && isListedInWorld(player, readiness.world);
    }

    static boolean isCurrent(EntityPlayerMP player, WorldServer world) {
        return hasOpenConnection(player) && player.connection.player == player && isListedInWorld(player, world);
    }

    private static boolean hasOpenConnection(EntityPlayerMP player) {
        NetHandlerPlayServer connection = player.connection;
        return connection != null && connection.getNetworkManager().isChannelOpen();
    }

    static boolean canAccept(EntityPlayerMP player, ManaReadiness readiness) {
        if (!readiness.complete) return false;
        if (readiness.lifecycle == DIMENSION_TRANSFER || readiness.lifecycle == TRANSFER_RECOVERY) {
            return isInstalledInWorld(player, readiness.world);
        }
        return isCurrent(player, readiness.world);
    }

    private static boolean isListedInWorld(EntityPlayerMP player, WorldServer world) {
        MinecraftServer server = player.getServer();
        return world != null && player.world == world && server != null
                && server.getWorld(player.dimension) == world
                && server.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player;
    }

    private static boolean isInstalledInWorld(EntityPlayerMP player, WorldServer world) {
        return world != null && world.provider.getDimension() == player.dimension && player.isEntityAlive()
                && player.interactionManager != null && player.interactionManager.world == world
                && world.getEntityByID(player.getEntityId()) == player && isCurrent(player, world);
    }

    private static ManaState managedState(EntityPlayerMP player) {
        ManaState state = player.getCapability(ManaCapability.INSTANCE, null);
        return state == null || state.readiness == null ? null : ManaService.requireServerState(player);
    }

    private static void bindWorld(ManaReadiness readiness, WorldServer world) {
        readiness.world = world;
        readiness.bound = true;
    }

    private static ManaReadiness newGeneration(EntityPlayerMP player, GameplayRules rules,
            ManaReadiness.Lifecycle lifecycle) {
        ManaReadiness readiness = new ManaReadiness(ManaProviderRegistry.instance().eligibleReadinessProviders(rules),
                player.getServer().getTickCounter(), false);
        readiness.lifecycle = lifecycle;
        return readiness;
    }

    private static void clearContexts(EntityPlayerMP player) {
        ManaProviderRegistry.instance().remove(player);
        ManaService.clearLifecycleContexts(player);
        ParticipantUseService.cancelUse(player);
    }

    static final class Transfer {
        final Transfer previous;
        final ManaReadiness source;
        final ManaReadiness destination;
        final WorldServer sourceWorld;
        final int sourceDimension;
        final NetHandlerPlayServer sourceConnection;
        final boolean sourceRecovering;
        boolean mutated;

        Transfer(EntityPlayerMP player, ManaState state) {
            previous = state.transfer;
            source = state.readiness;
            destination = newGeneration(player, GameplayRulesManager.current(), DIMENSION_TRANSFER);
            sourceWorld = player.getServerWorld();
            sourceDimension = player.dimension;
            sourceConnection = player.connection;
            sourceRecovering = state.recoveringTransfer;
        }
    }
}
