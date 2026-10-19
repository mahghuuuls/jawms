package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.jawms.internal.casting.ServerWandBindings;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Container.class)
public abstract class ContainerWandPublicationMixin {
    @WrapMethod(method = "detectAndSendChanges", require = 1, expect = 1)
    private void jawms$copyScope(Operation<Void> original) {
        ServerWandBindings.beginCopies();
        try { original.call(); } finally { ServerWandBindings.endCopies(); }
    }
    @WrapOperation(method = "detectAndSendChanges", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;copy()Lnet/minecraft/item/ItemStack;"), require = 1, expect = 1)
    private ItemStack jawms$sourceBeforeCopy(ItemStack live, Operation<ItemStack> original, @Local int slot) {
        ServerWandBindings.Source source = ServerWandBindings.beforeCopy(((Container) (Object) this).getSlot(slot), live);
        ItemStack snapshot = original.call(live);
        ServerWandBindings.copied(source, snapshot);
        return snapshot;
    }
}
