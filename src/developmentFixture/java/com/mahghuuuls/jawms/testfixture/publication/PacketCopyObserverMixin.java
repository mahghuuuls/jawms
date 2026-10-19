package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SPacketSetSlot.class, remap = false)
public abstract class PacketCopyObserverMixin {
    // The empty decoding constructor has no copy; select the sending constructor explicitly.
    @WrapOperation(method = "<init>(IILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;copy()Lnet/minecraft/item/ItemStack;"),
            require = 1, expect = 1, allow = 1)
    private ItemStack observedCopy(ItemStack original, Operation<ItemStack> copy) {
        boolean tracked = PublicationFixture.beforePacketCopy(original);
        ItemStack snapshot = copy.call(original);
        if (tracked) PublicationFixture.afterPacketCopy(original, snapshot);
        return snapshot;
    }
}
