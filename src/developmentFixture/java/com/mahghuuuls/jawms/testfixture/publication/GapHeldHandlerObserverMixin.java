package com.mahghuuuls.jawms.testfixture.publication;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import com.mahghuuuls.jawms.testfixture.client.GapClientObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = NetHandlerPlayClient.class, remap = false)
public abstract class GapHeldHandlerObserverMixin {
    @WrapMethod(method = "handleHeldItemChange")
    private void observed(SPacketHeldItemChange packet, Operation<Void> original) {
        if (!ContinuousTransportFixture.gapObserving() || !Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            original.call(packet); return;
        }
        GapClientObservation.beginSelection(packet);
        try { original.call(packet); } finally { GapClientObservation.endSelection(); }
    }
}
