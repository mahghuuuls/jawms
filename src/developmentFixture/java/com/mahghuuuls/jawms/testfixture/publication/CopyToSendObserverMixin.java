package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerMP.class, remap = false)
public abstract class CopyToSendObserverMixin {
    @Inject(method = "sendSlotContents", at = @At("HEAD"))
    private void observedCallback(Container container, int slot, ItemStack snapshot, CallbackInfo callback) {
        PublicationFixture.copyToSend((EntityPlayerMP) (Object) this, container, slot, snapshot);
    }
}
