package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.util.FoodStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemArtefact.class, remap = false)
public abstract class ItemArtefactMixin {

    @Redirect(method = "onSpellCastPreEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/FoodStats;addStats(IF)V"),
            require = 1, expect = 1, remap = false)
    private static void jawms$disableHungerCastingFallback(
            FoodStats foodStats, int foodLevel, float saturation
    ) {
        // JAWMS player mana is authoritative; Hunger Casting must not replace it.
    }
}
