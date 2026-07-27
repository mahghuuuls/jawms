package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.Tags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ManaEvents {

    private static final ResourceLocation CAPABILITY_ID = new ResourceLocation(Tags.MOD_ID, "player_mana");

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(CAPABILITY_ID, new ManaProvider());
        }
    }

    @SubscribeEvent
    public void clonePlayer(Clone event) {
        ManaState original = event.getOriginal().getCapability(ManaCapability.INSTANCE, null);
        ManaState replacement = event.getEntityPlayer().getCapability(ManaCapability.INSTANCE, null);
        if (original != null && replacement != null) {
            replacement.copyFrom(original);
        }
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        ManaState state = event.player.getCapability(ManaCapability.INSTANCE, null);
        if (state != null && state.regenerateTick(ManaState.DEFAULT_REGENERATION_PER_SECOND) > 0) {
            ManaNetwork.synchronize(event.player, state);
        }
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ManaNetwork.synchronizeLifecycle(event.player, "login");
    }

    @SubscribeEvent
    public void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        ManaNetwork.synchronizeLifecycle(event.player, "respawn");
    }

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ManaNetwork.synchronizeLifecycle(event.player, "dimension change");
    }
}
