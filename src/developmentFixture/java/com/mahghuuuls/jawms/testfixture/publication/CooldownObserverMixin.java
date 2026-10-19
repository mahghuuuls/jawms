package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import electroblob.wizardry.util.WandHelper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WandHelper.class, remap = false)
public abstract class CooldownObserverMixin {
    @Inject(method = "setCurrentCooldown", at = @At("HEAD"))
    private static void observedCooldown(ItemStack stack, int value, CallbackInfo callback) { PublicationFixture.cooldown(stack, value); }
}
