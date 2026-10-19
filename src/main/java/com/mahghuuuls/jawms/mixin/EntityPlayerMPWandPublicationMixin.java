package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mahghuuuls.jawms.internal.casting.ServerWandBindings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPWandPublicationMixin {
    @WrapOperation(method = "sendSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"), require = 1, expect = 1)
    private void jawms$sendSlot(NetHandlerPlayServer connection, Packet<?> packet, Operation<Void> original,
            @Local(argsOnly = true) Container container, @Local(argsOnly = true) int slot,
            @Local(argsOnly = true) ItemStack snapshot) {
        ServerWandBindings.sendSlot((EntityPlayerMP) (Object) this, container.windowId, slot,
                ServerWandBindings.copiedSource(snapshot), packet);
    }

    @WrapOperation(method = "sendAllContents", at = @At(value = "NEW",
            target = "(ILnet/minecraft/util/NonNullList;)Lnet/minecraft/network/play/server/SPacketWindowItems;"), require = 1, expect = 1)
    private SPacketWindowItems jawms$bulkSources(int window, NonNullList<ItemStack> items,
            Operation<SPacketWindowItems> original, @Local(argsOnly = true) Container container,
            @Share("jawms_sources") LocalRef<ServerWandBindings.Source[]> shared) {
        ServerWandBindings.Source[] sources = new ServerWandBindings.Source[items.size()];
        for (int slot = 0; slot < sources.length; slot++) sources[slot] = ServerWandBindings.liveSource(
                (EntityPlayerMP) (Object) this, container, slot, items.get(slot));
        shared.set(sources);
        return original.call(window, items);
    }
    @WrapOperation(method = "sendAllContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V", ordinal = 0), require = 1, expect = 1)
    private void jawms$sendWindow(NetHandlerPlayServer connection, Packet<?> packet, Operation<Void> original,
            @Local(argsOnly = true) Container container,
            @Share("jawms_sources") LocalRef<ServerWandBindings.Source[]> shared) {
        ServerWandBindings.sendWindow((EntityPlayerMP) (Object) this, container.windowId, shared.get(), packet);
    }
}
