package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemArtefact.class, remap = false)
public abstract class ItemArtefactMixin {

    @Redirect(
            method = "onSpellCastPreEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/IManaStoringItem;"
                            + "getMana(Lnet/minecraft/item/ItemStack;)I",
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private static int jawms$disableHungerCastingCondition(
            IManaStoringItem manaItem, ItemStack stack
    ) {
        return manaItem.getManaCapacity(stack);
    }

    @Redirect(
            method = "onSpellCastPreEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/FoodStats;getFoodLevel()I",
                    remap = true
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private static int jawms$disableHungerCastingPaymentCheck(FoodStats foodStats) {
        return Integer.MIN_VALUE;
    }

    @Redirect(method = "onSpellCastPreEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/FoodStats;addStats(IF)V", remap = true),
            require = 1, expect = 1, remap = false)
    private static void jawms$disableHungerCastingFallback(
            FoodStats foodStats, int foodLevel, float saturation
    ) {
        // JAWMS player mana is authoritative; Hunger Casting must not replace it.
    }
}
