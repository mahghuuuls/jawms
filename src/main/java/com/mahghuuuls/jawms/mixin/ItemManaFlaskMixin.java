package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryFlaskBridge;
import electroblob.wizardry.item.ItemManaFlask;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.util.EntityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemManaFlask.class, remap = false)
public abstract class ItemManaFlaskMixin {

    private static final String RIGHT_CLICK =
            "onItemRightClick(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;";
    private static final String RESTORE =
            "findAndChargeItem(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;)V";

    @Shadow @Final public ItemManaFlask.Size size;

    @Shadow
    private void findAndChargeItem(ItemStack stack, EntityPlayer player) {
        throw new AssertionError();
    }

    @Inject(
            method = RIGHT_CLICK,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$replaceEligibility(
            World world,
            EntityPlayer player,
            EnumHand hand,
            CallbackInfoReturnable<ActionResult<ItemStack>> callback
    ) {
        ItemStack flask = player.getHeldItem(hand);
        if (!WizardryFlaskBridge.canRestore(player, size.capacity)) {
            callback.setReturnValue(new ActionResult<>(EnumActionResult.FAIL, flask));
            return;
        }
        if (player.capabilities.isCreativeMode) {
            findAndChargeItem(flask, player);
        } else {
            player.setActiveHand(hand);
        }
        callback.setReturnValue(new ActionResult<>(EnumActionResult.SUCCESS, flask));
    }

    @Inject(
            method = RESTORE,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$replaceRestoration(
            ItemStack stack,
            EntityPlayer player,
            CallbackInfo callback
    ) {
        if (!player.world.isRemote && WizardryFlaskBridge.restore(player, size.capacity)) {
            EntityUtils.playSoundAtPlayer(player, WizardrySounds.ITEM_MANA_FLASK_USE, 1, 1);
            EntityUtils.playSoundAtPlayer(player, WizardrySounds.ITEM_MANA_FLASK_RECHARGE, 0.7F, 1.1F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            player.getCooldownTracker().setCooldown((ItemManaFlask) (Object) this, 20);
        }
        callback.cancel();
    }
}
