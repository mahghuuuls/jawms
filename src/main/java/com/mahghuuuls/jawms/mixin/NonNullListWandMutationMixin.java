package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Mixin(NonNullList.class)
public abstract class NonNullListWandMutationMixin {
    @WrapOperation(method = "set", at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;", remap = false),
            require = 1, expect = 1)
    private Object jawms$actualInventoryWrite(List<Object> values, int index, Object value, Operation<Object> original) {
        Object previous = original.call(values, index, value);
        WandInventoryMutations.wrote(this, index, previous, value);
        return previous;
    }
}
