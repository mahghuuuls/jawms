package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.Tags;
import com.mahghuuuls.jawms.internal.casting.WizardryWandBridge;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
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
        CastCostContextManager.clearAll(event.getOriginal());
        CastCostContextManager.clearAll(event.getEntityPlayer());
        if (event.getEntityPlayer().world.isRemote) {
            return;
        }
        ManaState original = event.getOriginal().getCapability(ManaCapability.INSTANCE, null);
        ManaState replacement = event.getEntityPlayer().getCapability(ManaCapability.INSTANCE, null);
        if (original != null && replacement != null) {
            ManaService.copyForClone(event.getEntityPlayer(), original);
        }
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        ManaState state = event.player.getCapability(ManaCapability.INSTANCE, null);
        GameplayRules rules = GameplayRulesManager.current();
        if (state != null) {
            WizardryWandBridge.reconcileContinuousCast(event.player);
            ManaService.tickPlayer(event.player, rules);
        }
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ManaNetwork.synchronizeRules(event.player, GameplayRulesManager.current());
        ManaNetwork.synchronizeLifecycle(event.player, "login");
    }

    @SubscribeEvent
    public void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        CastCostContextManager.clearAll(event.player);
        ManaNetwork.synchronizeRules(event.player, GameplayRulesManager.current());
        ManaNetwork.synchronizeLifecycle(event.player, "respawn");
    }

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ManaService.clearTransientLifecycleState(event.player);
        ManaNetwork.synchronizeRules(event.player, GameplayRulesManager.current());
        ManaNetwork.synchronizeLifecycle(event.player, "dimension change");
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ManaService.clearTransientLifecycleState(event.player);
        ManaProviderRegistry.instance().remove(event.player);
    }
}
