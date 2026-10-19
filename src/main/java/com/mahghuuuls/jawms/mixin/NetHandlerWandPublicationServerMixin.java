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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerWandPublicationServerMixin {
    @Shadow public EntityPlayerMP player;
    @Redirect(method = "processHeldItemChange", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD), require = 1, expect = 1)
    private void jawms$selection(InventoryPlayer inventory, int selected) { WandInventoryMutations.select(inventory, selected); }

    @WrapOperation(method = "processCustomPayload", at = @At(value = "NEW",
            target = "(IILnet/minecraft/item/ItemStack;)Lnet/minecraft/network/play/server/SPacketSetSlot;"), require = 2, expect = 2)
    private SPacketSetSlot jawms$pickSource(int window, int slot, ItemStack live, Operation<SPacketSetSlot> original,
            @Share("jawms_source") LocalRef<ServerWandBindings.Source> source) {
        source.set(ServerWandBindings.directSource(player.inventory, slot, live));
        return original.call(window, slot, live);
    }
    @WrapOperation(method = "processCustomPayload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"), require = 3, expect = 3)
    private void jawms$pickSend(NetHandlerPlayServer connection, Packet<?> packet, Operation<Void> original,
            @Share("jawms_source") LocalRef<ServerWandBindings.Source> source) {
        if (packet instanceof SPacketSetSlot) ServerWandBindings.sendDirect(player, source.get(), packet);
        else original.call(connection, packet);
    }
}
