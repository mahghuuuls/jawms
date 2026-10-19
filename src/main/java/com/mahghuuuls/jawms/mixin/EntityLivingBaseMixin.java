package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryMeleeManaService;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "stopActiveHand", require = 1, expect = 1)
    private void jawms$closeReleasedUse(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        if (!(entity instanceof net.minecraft.entity.player.EntityPlayer) || entity.world.isRemote) {
            original.call(); return;
        }
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) entity;
        com.mahghuuuls.jawms.internal.casting.ParticipantUseService.ReleaseFrame frame =
                com.mahghuuuls.jawms.internal.casting.ParticipantUseService.beginRelease(player);
        boolean failed = true;
        try { original.call(); failed = false; }
        finally { com.mahghuuuls.jawms.internal.casting.ParticipantUseService.endRelease(player, frame, failed); }
    }

    @Redirect(
            method = "damageEntity(Lnet/minecraft/util/DamageSource;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeHooks;"
                            + "onLivingDamage(Lnet/minecraft/entity/EntityLivingBase;"
                            + "Lnet/minecraft/util/DamageSource;F)F",
                    remap = false
            ),
            require = 1,
            expect = 1
    )
    private float jawms$applyMeleeAtFinalDamage(
            EntityLivingBase target, DamageSource source, float amount
    ) {
        float finalDamage = ForgeHooks.onLivingDamage(target, source, amount);
        return WizardryMeleeManaService.applyFinalDamage(target, source, finalDamage);
    }
}
