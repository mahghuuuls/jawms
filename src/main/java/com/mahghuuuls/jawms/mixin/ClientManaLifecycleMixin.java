package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.client.ClientManaState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.network.play.server.SPacketCombatEvent;
import net.minecraft.network.play.server.SPacketRespawn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public abstract class ClientManaLifecycleMixin {
    @Unique
    private static final String AFTER_HANDOFF =
            "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V";

    @Inject(method = "handleCombatEvent", at = @At(value = "INVOKE", target = AFTER_HANDOFF,
            shift = At.Shift.AFTER), require = 1)
    private void jawms$hideManaOnDeath(SPacketCombatEvent packet, CallbackInfo callback) {
        Minecraft client = Minecraft.getMinecraft();
        if (packet.eventType == SPacketCombatEvent.Event.ENTITY_DIED && client.player != null && client.world != null
                && client.world.getEntityByID(packet.playerId) == client.player) {
            ClientManaState.invalidateLifecycle(this, client.getConnection());
        }
    }

    @Inject(method = "handleChangeGameState", at = @At(value = "INVOKE", target = AFTER_HANDOFF,
            shift = At.Shift.AFTER), require = 1)
    private void jawms$hideManaOnEndExit(SPacketChangeGameState packet, CallbackInfo callback) {
        if (packet.getGameState() == 4) jawms$invalidateManaView();
    }

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = AFTER_HANDOFF,
            shift = At.Shift.AFTER), require = 1)
    private void jawms$hideManaOnRespawn(SPacketRespawn packet, CallbackInfo callback) {
        jawms$invalidateManaView();
    }

    @Unique
    private void jawms$invalidateManaView() {
        ClientManaState.invalidateLifecycle(this, Minecraft.getMinecraft().getConnection());
    }
}
