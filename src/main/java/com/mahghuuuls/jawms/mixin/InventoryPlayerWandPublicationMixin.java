package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mahghuuuls.jawms.internal.casting.ServerWandBindings;
import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketSetSlot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InventoryPlayer.class)
public abstract class InventoryPlayerWandPublicationMixin {
    // Includes wheel/pick/copy writes that exist on the current side; no reference to stripped client methods.
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD), require = 1)
    private void jawms$selection(InventoryPlayer inventory, int selected) { WandInventoryMutations.select(inventory, selected); }

    @WrapOperation(method = "placeItemBackInInventory", at = @At(value = "NEW",
            target = "(IILnet/minecraft/item/ItemStack;)Lnet/minecraft/network/play/server/SPacketSetSlot;"), require = 1, expect = 1)
    private SPacketSetSlot jawms$directSource(int window, int slot, ItemStack live, Operation<SPacketSetSlot> original,
            @Share("jawms_source") LocalRef<ServerWandBindings.Source> source) {
        source.set(ServerWandBindings.directSource((InventoryPlayer) (Object) this, slot, live));
        return original.call(window, slot, live);
    }
    @WrapOperation(method = "placeItemBackInInventory", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"), require = 1, expect = 1)
    private void jawms$directSend(NetHandlerPlayServer connection, Packet<?> packet, Operation<Void> original,
            @Share("jawms_source") LocalRef<ServerWandBindings.Source> source) {
        ServerWandBindings.sendDirect((EntityPlayerMP) ((InventoryPlayer) (Object) this).player, source.get(), packet);
    }
}
