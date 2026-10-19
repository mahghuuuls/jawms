package com.mahghuuuls.jawms.testfixture.publication;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = { SPacketSetSlot.class, SPacketWindowItems.class }, remap = false)
public abstract class PacketCopyObserverMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;copy()Lnet/minecraft/item/ItemStack;"))
    private ItemStack observedCopy(ItemStack original, Operation<ItemStack> copy) {
        boolean tracked = PublicationFixture.beforePacketCopy(original);
        ItemStack snapshot = copy.call(original);
        if (tracked) PublicationFixture.afterPacketCopy(original, snapshot);
        return snapshot;
    }
}
