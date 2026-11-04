package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.internal.clientbridge.ClientManaViewBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;

public final class ClientManaViewAdapter implements ClientManaViewBridge.View {

    @Override
    public ClientManaSnapshot getSnapshot() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        NetHandlerPlayClient connection = player == null ? null : player.connection;
        return ClientManaState.snapshot(connection != null && connection.getNetworkManager().isChannelOpen()
                ? connection : null);
    }
}
