package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinecraftWandSelectionClientMixin {
    @Redirect(method = "processKeyBinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD), require = 1, expect = 1)
    private void jawms$keyboardSelection(InventoryPlayer inventory, int selected) { WandInventoryMutations.select(inventory, selected); }
}
