package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import com.mahghuuuls.jawms.internal.casting.WandBinding;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mahghuuuls.jawms.client.ClientParticipantUseGuard$Frame", remap = false)
public abstract class ClientCaptureObserverMixin {
    @Inject(method = "<init>(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumHand;Lelectroblob/wizardry/spell/Spell;Lcom/mahghuuuls/jawms/internal/casting/WandBinding;)V", at = @At("RETURN"))
    private void observedCapture(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell, WandBinding binding, CallbackInfo callback) {
        PublicationFixture.capture(player, stack, this);
        com.mahghuuuls.jawms.testfixture.client.ContinuousTransportObserver.captureObserved(player, hand, this);
    }
}
