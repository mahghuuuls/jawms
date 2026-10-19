package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;

import com.mahghuuuls.jawms.client.ClientWandPublications;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.client.Minecraft;
import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import com.mahghuuuls.jawms.client.ClientParticipantUseGuard;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientWandPublications.class, remap = false)
public abstract class ClientApplicationObserverMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private static void observedApply(Packet<?> packet, NetHandlerPlayClient handler, Runnable vanilla, CallbackInfo callback) {
        PublicationFixture.packet("main_apply_scope", packet);
    }
    @Inject(method = "apply", at = @At("RETURN"))
    private static void observedInstall(Packet<?> packet, NetHandlerPlayClient handler, Runnable vanilla, CallbackInfo callback) {
        if (!PublicationFixture.enabled() || Minecraft.getMinecraft().player == null) return;
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(handler.getNetworkManager());
        if (hook == null) return;
        WandPublicationRecord record = hook.application(packet);
        if (record == null) return;
        boolean installed = false;
        for (WandPublicationRecord.Entry entry : record.entries()) {
            net.minecraft.item.ItemStack decoded = packet instanceof SPacketSetSlot ? ((SPacketSetSlot) packet).getStack()
                    : ((SPacketWindowItems) packet).getItemStacks().get(entry.windowSlot);
            if (Minecraft.getMinecraft().player.inventory.getStackInSlot(entry.inventorySlot) == decoded) installed = true;
        }
        if (installed) {
            try {
                java.lang.reflect.Field field = ClientParticipantUseGuard.class.getDeclaredField("state"); field.setAccessible(true);
                hook.publicationApplied(record, ((ClientUseState) field.get(null)).pending());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        } else PublicationFixture.log("ignored_vanilla_write", "serial", record.serial, "verdict", "UNREACHED");
    }
}
