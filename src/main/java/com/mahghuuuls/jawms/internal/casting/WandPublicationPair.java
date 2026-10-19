package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.Unpooled;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;

/** Local outbound queue item expanded at dispatch, never registered or serialized on the wire. */
public final class WandPublicationPair implements Packet<INetHandler> {
    // Deliberately not registered as an FML channel: Forge must forward the vanilla payload unchanged.
    public static final String CHANNEL = "JAWMS|Wand";
    public final Packet<?> marker, original;
    public WandPublicationPair(WandPublicationRecord record, Packet<?> original) {
        if (record.kind == WandPublicationRecord.RETIRE) throw new IllegalArgumentException("Retirement has no partner");
        this.marker = marker(record); this.original = java.util.Objects.requireNonNull(original, "original");
    }
    public static SPacketCustomPayload marker(WandPublicationRecord record) {
        PacketBuffer data = new PacketBuffer(Unpooled.buffer(WandPublicationRecord.MAX_BYTES));
        record.write(data);
        return new SPacketCustomPayload(CHANNEL, data);
    }
    public void readPacketData(PacketBuffer data) { throw new UnsupportedOperationException("Local dispatch only"); }
    public void writePacketData(PacketBuffer data) { throw new UnsupportedOperationException("Local dispatch only"); }
    public void processPacket(INetHandler handler) { throw new UnsupportedOperationException("Local dispatch only"); }
}
