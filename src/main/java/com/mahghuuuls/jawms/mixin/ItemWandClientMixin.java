package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Client-only removal of Wizardry's advanced wand-mana tooltip line. */
@Mixin(value = ItemWand.class, remap = false)
public abstract class ItemWandClientMixin {

    private static final String ADD_INFORMATION =
            "addInformation(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/world/World;Ljava/util/List;"
                    + "Lnet/minecraft/client/util/ITooltipFlag;)V";

    @Inject(
            method = ADD_INFORMATION,
            at = @At("RETURN"),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$removeAdvancedManaLine(
            ItemStack stack,
            World world,
            List<String> tooltip,
            ITooltipFlag flag,
            CallbackInfo callback
    ) {
        ItemWand wand = (ItemWand) (Object) this;
        tooltip.remove(Wizardry.proxy.translate(
                "item." + Wizardry.MODID + ":wand.mana",
                new Style().setColor(TextFormatting.BLUE),
                wand.getMana(stack),
                wand.getManaCapacity(stack)
        ));
    }
}
