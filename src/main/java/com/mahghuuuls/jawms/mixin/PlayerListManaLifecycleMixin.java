package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.jawms.internal.mana.ManaLifecycle;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListManaLifecycleMixin {
    @Unique
    private static final String TRANSFER = "transferPlayerToDimension"
            + "(Lnet/minecraft/entity/player/EntityPlayerMP;ILnet/minecraftforge/common/util/ITeleporter;)V";

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

    @Inject(method = "recreatePlayerEntity", at = @At("HEAD"), require = 1)
    private void jawms$retireReplacement(EntityPlayerMP player, int dimension, boolean endReturn,
            CallbackInfoReturnable<EntityPlayerMP> callback) {
        ManaLifecycle.retire(player);
    }

    @WrapOperation(method = "recreatePlayerEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;firePlayerRespawnEvent(Lnet/minecraft/entity/player/EntityPlayer;Z)V",
            remap = false), require = 1)
    private void jawms$bindReplacement(FMLCommonHandler forge, EntityPlayer player, boolean endReturn,
            Operation<Void> original) {
        ManaLifecycle.bindReplacement((EntityPlayerMP) player, endReturn);
        original.call(forge, player, endReturn);
    }

    @Inject(method = "recreatePlayerEntity", at = @At("RETURN"), require = 1)
    private void jawms$finishReplacement(EntityPlayerMP player, int dimension, boolean endReturn,
            CallbackInfoReturnable<EntityPlayerMP> callback) {
        ManaLifecycle.finishReplacement(callback.getReturnValue());
    }

    @WrapMethod(method = TRANSFER, remap = false, require = 1)
    private void jawms$holdTransfer(EntityPlayerMP player, int dimension, ITeleporter teleporter,
            Operation<Void> original) {
        ManaLifecycle.transfer(player, () -> original.call(player, dimension, teleporter));
    }

    @Inject(method = TRANSFER, remap = false, at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;dimension:I",
            shift = At.Shift.AFTER, remap = true), require = 1)
    private void jawms$markTransferMutation(EntityPlayerMP player, int dimension, ITeleporter teleporter,
            CallbackInfo callback) {
        ManaLifecycle.transferMutated(player);
    }

    @WrapOperation(method = TRANSFER, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;firePlayerChangedDimensionEvent(Lnet/minecraft/entity/player/EntityPlayer;II)V",
            remap = false), require = 1)
    private void jawms$bindTransferDestination(FMLCommonHandler forge, EntityPlayer player, int from, int to,
            Operation<Void> original) {
        ManaLifecycle.bindTransferDestination((EntityPlayerMP) player);
        original.call(forge, player, from, to);
    }
}

