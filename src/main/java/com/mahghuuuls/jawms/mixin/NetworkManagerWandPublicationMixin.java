package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerWandPublicationMixin {
    @Shadow private Channel channel;
    @Shadow private INetHandler packetListener;
    @Shadow private void dispatchPacket(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        throw new AssertionError();
    }

    @Inject(method = "dispatchPacket", at = @At("HEAD"), cancellable = true, require = 1, expect = 1)
    private void jawms$dispatchPair(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>[] listeners,
            CallbackInfo callback) {
        if (!(packet instanceof WandPublicationPair)) return;
        callback.cancel(); // The local queue item must never reach EnumConnectionState/packet encoding.
        WandPublicationPair pair = (WandPublicationPair) packet;
        Runnable write = () -> { dispatchPacket(pair.marker, null); dispatchPacket(pair.original, listeners); };
        if (channel.eventLoop().inEventLoop()) write.run(); else channel.eventLoop().execute(write);
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1)
    private void jawms$associateDecodedPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (JustAnotherWizardryManaSystemMod.proxy != null
                && !JustAnotherWizardryManaSystemMod.proxy.allowWandPacket((NetworkManager) (Object) this, packetListener, packet))
            callback.cancel();
    }
}
