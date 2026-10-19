package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.client.ClientParticipantUseGuard;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs before vanilla queues its use/release packet on the same connection. */
@Mixin(PlayerControllerMP.class)
public abstract class PlayerControllerParticipantUseMixin {
    @Inject(method = "processRightClick", at = @At("HEAD"), cancellable = true, require = 1, expect = 1)
    private void jawms$announceUse(EntityPlayer player, World world, EnumHand hand,
            CallbackInfoReturnable<EnumActionResult> callback) {
        if (player.getHeldItem(hand).getItem() instanceof ItemWand && !ClientParticipantUseGuard.input(player, hand)) {
            callback.setReturnValue(EnumActionResult.FAIL);
        }
    }
    @Inject(method = "onStoppedUsingItem", at = @At("HEAD"), require = 1, expect = 1)
    private void jawms$announceRelease(EntityPlayer player, CallbackInfo callback) {
        ClientParticipantUseGuard.release();
    }
}
