package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.jawms.internal.mana.ManaLifecycle;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerManaLifecycleMixin {
    @WrapOperation(method = "onDeath", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDeath(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/util/DamageSource;)Z",
            remap = false), require = 1)
    private boolean jawms$guardDeath(EntityLivingBase player, DamageSource cause, Operation<Boolean> forge) {
        return ManaLifecycle.checkDeath((EntityPlayerMP) player, () -> forge.call(player, cause));
    }

    @Inject(method = "changeDimension(ILnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/entity/Entity;",
            remap = false, at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;removeEntity(Lnet/minecraft/entity/Entity;)V",
                    remap = true), require = 1)
    private void jawms$retireAtEndExit(int dimension, ITeleporter teleporter, CallbackInfoReturnable<?> callback) {
        ManaLifecycle.retire((EntityPlayerMP) (Object) this);
    }
}
