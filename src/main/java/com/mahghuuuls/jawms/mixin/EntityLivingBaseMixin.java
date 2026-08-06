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
