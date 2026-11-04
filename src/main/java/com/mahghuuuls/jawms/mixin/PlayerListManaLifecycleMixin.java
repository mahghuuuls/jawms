package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.mana.ManaService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListManaLifecycleMixin {

    @Inject(method = "initializeConnectionToPlayer", remap = false, at = @At("HEAD"), require = 1)
    private void jawms$bindLogin(NetworkManager network, EntityPlayerMP player,
            NetHandlerPlayServer handler, CallbackInfo callback) {
        ManaService.bindLogin(player);
    }

    @Inject(method = "initializeConnectionToPlayer", remap = false, at = @At("RETURN"), require = 1)
    private void jawms$finishLogin(NetworkManager network, EntityPlayerMP player,
            NetHandlerPlayServer handler, CallbackInfo callback) {
        ManaService.finishLogin(player);
    }

    @Inject(method = "playerLoggedOut", at = @At("HEAD"), require = 1)
    private void jawms$retireLogout(EntityPlayerMP player, CallbackInfo callback) {
        ManaService.retireLogout(player);
    }
}

