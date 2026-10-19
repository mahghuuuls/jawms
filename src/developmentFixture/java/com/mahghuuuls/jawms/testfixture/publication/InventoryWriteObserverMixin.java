package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Mixin(value = NonNullList.class, remap = false)
public abstract class InventoryWriteObserverMixin {
    @WrapOperation(method = "set", at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object observedWrite(List<Object> list, int index, Object value, Operation<Object> original) {
        Object previous = original.call(list, index, value);
        if (previous instanceof ItemStack && value instanceof ItemStack)
            PublicationFixture.write(this, index, (ItemStack) previous, (ItemStack) value);
        return previous;
    }
}
