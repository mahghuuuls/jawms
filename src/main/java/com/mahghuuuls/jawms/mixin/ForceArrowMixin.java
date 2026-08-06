package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.internal.casting.WizardryWandBridge;
import electroblob.wizardry.entity.projectile.EntityForceArrow;
import electroblob.wizardry.spell.ForceArrow;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = ForceArrow.class, remap = false)
public abstract class ForceArrowMixin {

    @Inject(
            method = "addArrowExtras(Lelectroblob/wizardry/entity/projectile/"
                    + "EntityForceArrow;Lnet/minecraft/entity/EntityLivingBase;"
                    + "Lelectroblob/wizardry/util/SpellModifiers;)V",
            at = @At("TAIL"),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$capturePendingForceArrow(
            EntityForceArrow arrow,
            @Nullable EntityLivingBase caster,
            SpellModifiers modifiers,
            CallbackInfo callback
    ) {
        if (caster != null) {
            WizardryWandBridge.capturePendingForceArrow(arrow, caster);
        }
    }
}
