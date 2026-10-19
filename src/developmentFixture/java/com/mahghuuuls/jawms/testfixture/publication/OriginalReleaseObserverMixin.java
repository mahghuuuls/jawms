package com.mahghuuuls.jawms.testfixture.publication;

import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemWand.class, remap = false)
public abstract class OriginalReleaseObserverMixin {
    @Inject(method = "onPlayerStoppedUsing", at = @At("HEAD"))
    private void observedRelease(ItemStack stack, World world, EntityLivingBase caster, int timeLeft, CallbackInfo callback) {
        if (caster instanceof EntityPlayer) PublicationFixture.release((EntityPlayer) caster, stack, timeLeft);
    }
}
