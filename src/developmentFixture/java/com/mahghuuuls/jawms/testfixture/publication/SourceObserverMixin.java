package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import com.mahghuuuls.jawms.internal.casting.ServerWandBindings;
import net.minecraft.inventory.Slot;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerWandBindings.class, remap = false)
public abstract class SourceObserverMixin {
    @Inject(method = "witness", at = @At("RETURN"))
    private void observedWitness(InventoryPlayer inventory, int slot, ItemStack original, ItemStack snapshot,
            CallbackInfoReturnable<ServerWandBindings.Source> callback) {
        PublicationFixture.source("live_source_witness", callback.getReturnValue(), original, snapshot);
    }
    @Inject(method = "beforeCopy", at = @At("RETURN"))
    private static void observedSource(Slot slot, ItemStack original, CallbackInfoReturnable<ServerWandBindings.Source> callback) {
        PublicationFixture.source("before_copy", callback.getReturnValue(), original, original);
    }
    @Inject(method = "copied", at = @At("HEAD"))
    private static void observedCopy(ServerWandBindings.Source source, ItemStack snapshot, CallbackInfo callback) {
        PublicationFixture.copied(source, snapshot);
    }
    @Inject(method = "send", at = @At("HEAD"))
    private void observedSend(Packet<?> packet, int kind, int window, ServerWandBindings.Source[] sources, int[] slots, CallbackInfo callback) {
        PublicationFixture.sources(sources, packet, window);
    }
}
