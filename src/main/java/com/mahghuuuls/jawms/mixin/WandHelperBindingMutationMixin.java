package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import electroblob.wizardry.util.WandHelper;
import electroblob.wizardry.spell.Spell;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WandHelper.class, remap = false)
public abstract class WandHelperBindingMutationMixin {
    @Inject(method = "selectSpell", at = @At("RETURN"), require = 1)
    private static void jawms$selected(ItemStack stack, int slot, CallbackInfoReturnable<Boolean> callback) {
        WandInventoryMutations.dataChanged(stack);
    }
    @Inject(method = {"selectNextSpell", "selectPreviousSpell"}, at = @At("RETURN"), require = 1)
    private static void jawms$cycled(ItemStack stack, CallbackInfo callback) { WandInventoryMutations.dataChanged(stack); }
    @Inject(method = "setSpells", at = @At("RETURN"), require = 1)
    private static void jawms$boundSpells(ItemStack stack, Spell[] spells, CallbackInfo callback) {
        WandInventoryMutations.dataChanged(stack);
    }
    @Inject(method = "applyUpgrade", at = @At("RETURN"), require = 1)
    private static void jawms$upgraded(ItemStack stack, net.minecraft.item.Item upgrade, CallbackInfo callback) {
        WandInventoryMutations.dataChanged(stack);
    }
}
