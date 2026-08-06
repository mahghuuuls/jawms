package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.integration.wizardry.projectile.ForceArrowRefundTracker;
import electroblob.wizardry.entity.projectile.EntityForceArrow;
import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityForceArrow.class, remap = false)
public abstract class EntityForceArrowMixin {

    @Inject(
            method = "onEntityHit(Lnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("HEAD"),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$consumeRefundOnLivingHit(
            EntityLivingBase target, CallbackInfo callback
    ) {
        ForceArrowRefundTracker.onLivingHit((EntityForceArrow) (Object) this);
    }

    @Inject(
            method = "onBlockHit(Lnet/minecraft/util/math/RayTraceResult;)V",
            at = @At("HEAD"),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$refundOnBlockHit(
            RayTraceResult hit, CallbackInfo callback
    ) {
        ForceArrowRefundTracker.onBlockHit((EntityForceArrow) (Object) this);
    }

    @Inject(
            method = "onUpdate()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/entity/projectile/EntityForceArrow;"
                            + "returnManaToCaster()V",
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = true
    )
    private void jawms$refundOnExpiry(CallbackInfo callback) {
        ForceArrowRefundTracker.onExpiry((EntityForceArrow) (Object) this);
    }

    @Redirect(
            method = "returnManaToCaster()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/IManaStoringItem;"
                            + "rechargeMana(Lnet/minecraft/item/ItemStack;I)V",
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$disableLegacyWandManaRefund(
            IManaStoringItem item, ItemStack stack, int mana
    ) {
        // JAWMS restores only the captured player-mana payment.
    }
}
