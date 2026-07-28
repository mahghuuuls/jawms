package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mahghuuuls.jawms.internal.casting.WizardryWandBridge;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemWand.class, remap = false)
public abstract class ItemWandMixin {

    private static final String CAN_CAST =
            "canCast(Lnet/minecraft/item/ItemStack;"
                    + "Lelectroblob/wizardry/spell/Spell;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;"
                    + "ILelectroblob/wizardry/util/SpellModifiers;)Z";
    private static final String CAST =
            "cast(Lnet/minecraft/item/ItemStack;"
                    + "Lelectroblob/wizardry/spell/Spell;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;"
                    + "ILelectroblob/wizardry/util/SpellModifiers;)Z";

    @ModifyVariable(
            method = CAN_CAST,
            at = @At(value = "STORE", ordinal = 0),
            index = 7,
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceCanCastCost(
            int wizardryCost,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.replaceCalculatedCost(
                wizardryCost,
                spell,
                caster,
                modifiers
        );
    }

    @Redirect(
            method = CAN_CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;"
                            + "getMana(Lnet/minecraft/item/ItemStack;)I",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceWandAffordability(
            ItemWand wand,
            ItemStack queriedStack,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.getAvailableManaForPrecheck(
                wand.getMana(queriedStack),
                spell,
                caster,
                hand,
                modifiers
        );
    }

    @ModifyVariable(
            method = CAST,
            at = @At(value = "STORE", ordinal = 0),
            index = 8,
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceSuccessfulCastCost(
            int wizardryCost,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.replaceCalculatedCost(
                wizardryCost,
                spell,
                caster,
                modifiers
        );
    }

    @Redirect(
            method = CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/spell/Spell;"
                            + "cast(Lnet/minecraft/world/World;"
                            + "Lnet/minecraft/entity/player/EntityPlayer;"
                            + "Lnet/minecraft/util/EnumHand;"
                            + "ILelectroblob/wizardry/util/SpellModifiers;)Z",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private boolean jawms$castWithReservation(
            Spell spell,
            World world,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.castWithReservation(
                spell,
                world,
                caster,
                hand,
                castingTick,
                modifiers
        );
    }

    @Redirect(
            method = CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;"
                            + "consumeMana(Lnet/minecraft/item/ItemStack;"
                            + "ILnet/minecraft/entity/EntityLivingBase;)V",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$commitSuccessfulDeduction(
            ItemWand wand,
            ItemStack consumedStack,
            int wizardryCost,
            EntityLivingBase wielder,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        WizardryWandBridge.commitFromSuccessfulDeduction(
                wand,
                consumedStack,
                wizardryCost,
                wielder,
                spell
        );
    }

    @Inject(
            method = CAST,
            at = @At(
                    value = "FIELD",
                    target = "Lelectroblob/wizardry/spell/Spell;isContinuous:Z",
                    ordinal = 3,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$commitZeroCostSuccess(
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers,
            CallbackInfoReturnable<Boolean> callback
    ) {
        WizardryWandBridge.commitZeroCostIfPending(caster, spell);
    }

    @WrapMethod(method = CAST, require = 1, expect = 1, remap = false)
    private boolean jawms$cleanupReservation(
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers,
            Operation<Boolean> original
    ) {
        int initialDepth = WizardryWandBridge.captureAttemptDepth();
        try {
            return original.call(
                    stack,
                    spell,
                    caster,
                    hand,
                    castingTick,
                    modifiers
            );
        } finally {
            WizardryWandBridge.cleanupToDepth(caster, initialDepth);
        }
    }
}
