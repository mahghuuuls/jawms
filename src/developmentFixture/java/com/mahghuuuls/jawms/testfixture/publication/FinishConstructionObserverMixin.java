package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpellCastEvent.Finish.class, remap = false)
public abstract class FinishConstructionObserverMixin {
    @Inject(method = "<init>(Lelectroblob/wizardry/event/SpellCastEvent$Source;Lelectroblob/wizardry/spell/Spell;Lnet/minecraft/entity/EntityLivingBase;Lelectroblob/wizardry/util/SpellModifiers;I)V", at = @At("RETURN"))
    private void observedConstruction(SpellCastEvent.Source source, Spell spell, EntityLivingBase caster,
            SpellModifiers modifiers, int count, CallbackInfo callback) {
        PublicationFixture.constructed((SpellCastEvent.Finish) (Object) this);
    }
}
