package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import com.mahghuuuls.jawms.testfixture.client.GapClientObservation;
import net.minecraft.entity.player.InventoryPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WandInventoryMutations.class, remap = false)
public abstract class GapSelectionWriteObserverMixin {
    @Inject(method = "select", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private static void observed(InventoryPlayer inventory, int index, CallbackInfo callback) {
        GapClientObservation.selected(inventory, index);
    }
}
