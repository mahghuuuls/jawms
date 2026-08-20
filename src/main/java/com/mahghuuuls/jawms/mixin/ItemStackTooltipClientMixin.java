package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.client.FinalManaTooltipAssembler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Client-only terminal hook after Forge's ordinary item-tooltip event. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipClientMixin {

    @Inject(
            method = "getTooltip(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            require = 1,
            expect = 1,
            allow = 1
    )
    private void jawms$appendFinalContributionSection(
            EntityPlayer player,
            ITooltipFlag flags,
            CallbackInfoReturnable<List<String>> callback
    ) {
        FinalManaTooltipAssembler.append(
                (ItemStack) (Object) this,
                callback.getReturnValue()
        );
    }
}
