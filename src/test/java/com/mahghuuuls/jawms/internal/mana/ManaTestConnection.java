package com.mahghuuuls.jawms.internal.mana;

import io.netty.channel.Channel;
import io.netty.util.DefaultAttributeMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Provides an open test channel without a remote network dispatcher. */
final class ManaTestConnection {
    static {
        ManaNetwork.register();
    }

    private ManaTestConnection() {
    }

    static NetworkManager open(MinecraftServer server, EntityPlayerMP player) {
        NetworkManager network = mock(NetworkManager.class);
        Channel channel = mock(Channel.class);
        when(channel.attr(NetworkDispatcher.FML_DISPATCHER))
                .thenReturn(new DefaultAttributeMap().attr(NetworkDispatcher.FML_DISPATCHER));
        when(network.channel()).thenReturn(channel);
        when(network.isChannelOpen()).thenReturn(true);
        new NetHandlerPlayServer(server, network, player);
        return network;
    }
}
