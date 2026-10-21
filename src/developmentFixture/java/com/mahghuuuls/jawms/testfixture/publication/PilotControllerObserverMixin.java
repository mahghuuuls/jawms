package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.client.InputPilotDriver;
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

/** Observe real entry even when the production HEAD guard cancels; never changes the return. */
@Mixin(value = PlayerControllerMP.class, priority = 1100)
public abstract class PilotControllerObserverMixin {
    @Inject(method = "processRightClick", at = @At("HEAD"), require = 1, expect = 1)
    private void pilot$use(EntityPlayer player, World world, EnumHand hand, CallbackInfoReturnable<EnumActionResult> callback) {
        InputPilotDriver.controller(hand, false);
    }
    @Inject(method = "onStoppedUsingItem", at = @At("HEAD"), require = 1, expect = 1)
    private void pilot$release(EntityPlayer player, CallbackInfo callback) { InputPilotDriver.controller(null, true); }
}
