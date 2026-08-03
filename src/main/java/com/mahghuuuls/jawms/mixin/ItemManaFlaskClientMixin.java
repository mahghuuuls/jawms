package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.item.ItemManaFlask;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(value = ItemManaFlask.class, remap = false)
public abstract class ItemManaFlaskClientMixin {

    private static final String ADD_INFORMATION =
            "addInformation(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Ljava/util/List;Lnet/minecraft/client/util/ITooltipFlag;)V";

    @Shadow @Final public ItemManaFlask.Size size;

    @Inject(
            method = ADD_INFORMATION,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$replaceDescription(
            ItemStack stack,
            @Nullable World world,
            List<String> tooltip,
            ITooltipFlag flag,
            CallbackInfo callback
    ) {
        Wizardry.proxy.addMultiLineDescription(
                tooltip, "item.jawms.mana_flask.desc", size.capacity
        );
        callback.cancel();
    }
}
