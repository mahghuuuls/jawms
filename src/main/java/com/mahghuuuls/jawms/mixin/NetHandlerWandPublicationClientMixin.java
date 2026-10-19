package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mahghuuuls.jawms.client.ClientWandPublications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;
import net.minecraft.entity.player.InventoryPlayer;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerWandPublicationClientMixin {
    @Redirect(method = "handleHeldItemChange", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD), require = 1, expect = 1)
    private void jawms$serverSelection(InventoryPlayer inventory, int selected) {
        com.mahghuuuls.jawms.internal.casting.WandInventoryMutations.select(inventory, selected);
    }
    @WrapMethod(method = "handleSetSlot", require = 1, expect = 1)
    private void jawms$slotApplication(SPacketSetSlot packet, Operation<Void> original) {
        NetHandlerPlayClient handler = (NetHandlerPlayClient) (Object) this;
        PacketThreadUtil.checkThreadAndEnqueue(packet, handler, Minecraft.getMinecraft());
        ClientWandPublications.apply(packet, handler, () -> original.call(packet));
    }
    @WrapMethod(method = "handleWindowItems", require = 1, expect = 1)
    private void jawms$windowApplication(SPacketWindowItems packet, Operation<Void> original) {
        NetHandlerPlayClient handler = (NetHandlerPlayClient) (Object) this;
        PacketThreadUtil.checkThreadAndEnqueue(packet, handler, Minecraft.getMinecraft());
        ClientWandPublications.apply(packet, handler, () -> original.call(packet));
    }
}
