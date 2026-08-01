package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemWizardArmour.class, remap = false)
public abstract class ItemWizardArmourMixin {

    @Redirect(method = "applySpellModifiers", at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/SpellModifiers;set(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;",
            ordinal = 0, remap = false), require = 1, expect = 1, remap = false)
    private SpellModifiers jawms$removeMatchingArmorCost(
            SpellModifiers modifiers, String key, float value, boolean additive,
            EntityLivingBase caster, Spell spell, SpellModifiers argument
    ) {
        return modifiers;
    }

    @Redirect(method = "applySpellModifiers", at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/SpellModifiers;set(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;",
            ordinal = 1, remap = false), require = 1, expect = 1, remap = false)
    private SpellModifiers jawms$removeSageArmorCost(
            SpellModifiers modifiers, String key, float value, boolean additive,
            EntityLivingBase caster, Spell spell, SpellModifiers argument
    ) {
        return modifiers;
    }
}
