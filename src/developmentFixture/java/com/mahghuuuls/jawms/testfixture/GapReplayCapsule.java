package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/** One explicitly armed byte-only replay, never an archive of live connection objects. */
public final class GapReplayCapsule {
    static final long LIFETIME = 900_000_000_000L;
    static final int MAX_PAIR = 65536, MAX_FRAME = 512;
    private UUID owner;
    private long armedAt;
    private boolean disconnected;
    private byte[] marker, vanilla, close, release;

    public synchronized void arm(UUID owner, long now) {
        if (this.owner != null) {
            if (!this.owner.equals(owner)) clear();
            throw new IllegalStateException("Replay capsule already armed; clear explicitly");
        }
        this.owner = java.util.Objects.requireNonNull(owner); armedAt = now;
    }
    private void require(UUID operator, long now) {
        if (owner == null || !owner.equals(operator) || now - armedAt >= LIFETIME || now < armedAt) {
            clear(); throw new IllegalStateException("Replay capsule absent, wrong owner or expired; UNREACHED");
        }
    }
    public synchronized void pair(UUID operator, byte[] metadata, byte[] body, long now) {
        require(operator, now);
        if (disconnected) throw new IllegalStateException("Cannot replace disconnected capsule");
        if (close != null) return; // Freeze the last real pre-close publication.
        if (metadata.length > 256 || metadata.length + body.length > MAX_PAIR)
            throw new IllegalStateException("Replay pair exceeds fixture bound; UNREACHED");
        decodeMarker(metadata); decodeSlot(body);
        marker = metadata.clone(); vanilla = body.clone();
    }
    public synchronized void frame(UUID operator, byte[] bytes, boolean closing, long now) {
        require(operator, now);
        if (disconnected || bytes.length == 0 || bytes.length > MAX_FRAME)
            throw new IllegalStateException("Invalid replay frame; UNREACHED");
        PacketBuffer input = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        try {
            if (closing) {
                if (input.readUnsignedByte() != 7) throw new IllegalArgumentException("Not a close");
                ParticipantUseMessages.Close message = new ParticipantUseMessages.Close(); message.fromBytes(input);
                if (!message.isValid() || message.cause() != ParticipantUseMessages.CloseCause.NORMAL_FINISH
                        || message.hand() != net.minecraft.util.EnumHand.MAIN_HAND
                        || !new net.minecraft.util.ResourceLocation("jawms_cast_fixture", "stream").equals(message.spell()))
                    throw new IllegalArgumentException("Need a real normal close");
                close = bytes.clone();
            } else {
                if (input.readUnsignedByte() != 6) throw new IllegalArgumentException("Not a release");
                ParticipantUseMessages.Release message = new ParticipantUseMessages.Release(); message.fromBytes(input);
                if (!message.isValid()) throw new IllegalArgumentException("Invalid release");
                release = bytes.clone();
            }
        } finally { input.release(); }
    }
    public synchronized void disconnect(UUID operator, long now) {
        require(operator, now);
        if (disconnected || !complete()) { clear(); throw new IllegalStateException("Incomplete/second capsule disconnect; UNREACHED"); }
        disconnected = true;
    }
    public synchronized Snapshot inspect(UUID operator, long now) {
        require(operator, now);
        if (!complete()) throw new IllegalStateException("Incomplete replay capsule; UNREACHED");
        return new Snapshot(marker, vanilla, close, release, disconnected);
    }
    public synchronized Snapshot consume(UUID operator, UUID epoch, long intent, long use, long watermark, long now) {
        Snapshot result = inspect(operator, now);
        if (!disconnected || result.record.epoch.equals(epoch) || result.record.serial <= watermark
                || result.closed.intent() != intent || use != 0 && result.closed.use() != use)
            throw new IllegalStateException("Epoch replay discriminator not reached; UNREACHED");
        clear(); return result;
    }
    public synchronized boolean armed() { return owner != null; }
    public synchronized String status(UUID operator, long now) {
        require(operator, now);
        return "pair=" + (marker != null) + ",close=" + (close != null) + ",release=" + (release != null)
                + ",disconnected=" + disconnected + ",digest=" + (complete() ? inspect(operator, now).digest() : "incomplete");
    }
    public synchronized void clear() { owner = null; marker = vanilla = close = release = null; disconnected = false; }
    private boolean complete() { return marker != null && vanilla != null && close != null && release != null; }
    private static WandPublicationRecord decodeMarker(byte[] bytes) {
        PacketBuffer input = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        try {
            WandPublicationRecord record = WandPublicationRecord.read(input);
            WandPublicationRecord.Entry[] entries = record.entries();
            if (record.kind != WandPublicationRecord.SLOT || record.window != 0 || entries.length != 1
                    || entries[0].inventorySlot != 0 || entries[0].windowSlot != 36)
                throw new IllegalArgumentException("Replay requires the named mainhand single slot");
            return record;
        } finally { input.release(); }
    }
    private static SPacketSetSlot decodeSlot(byte[] bytes) {
        PacketBuffer input = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        try {
            SPacketSetSlot packet = new SPacketSetSlot(); packet.readPacketData(input);
            if (input.isReadable() || packet.getWindowId() != 0 || packet.getSlot() != 36)
                throw new IllegalArgumentException("Replay vanilla shape mismatch");
            return packet;
        } catch (IOException failure) { throw new IllegalArgumentException(failure); }
        finally { input.release(); }
    }
    public static byte[] encode(SPacketSetSlot packet) {
        PacketBuffer output = new PacketBuffer(Unpooled.buffer());
        try { packet.writePacketData(output); byte[] result = new byte[output.readableBytes()]; output.getBytes(0, result); return result; }
        catch (IOException failure) { throw new IllegalArgumentException(failure); }
        finally { output.release(); }
    }
    public static final class Snapshot {
        private final byte[] marker, vanilla, close, release;
        public final WandPublicationRecord record;
        public final ParticipantUseMessages.Close closed;
        public final boolean disconnected;
        Snapshot(byte[] marker, byte[] vanilla, byte[] close, byte[] release, boolean disconnected) {
            this.marker = marker.clone(); this.vanilla = vanilla.clone(); this.close = close.clone(); this.release = release.clone();
            this.disconnected = disconnected; record = decodeMarker(marker);
            PacketBuffer input = new PacketBuffer(Unpooled.wrappedBuffer(close));
            PacketBuffer released = new PacketBuffer(Unpooled.wrappedBuffer(release));
            try {
                input.readUnsignedByte(); closed = new ParticipantUseMessages.Close(); closed.fromBytes(input);
                released.readUnsignedByte(); ParticipantUseMessages.Release r = new ParticipantUseMessages.Release(); r.fromBytes(released);
                WandPublicationRecord.Entry e = record.entries()[0];
                if (!record.epoch.equals(closed.epoch()) || !record.epoch.equals(r.epoch()) || e.intent != closed.intent()
                        || e.intent != r.intent() || e.use != closed.use() || e.generation != closed.generation())
                    throw new IllegalArgumentException("Capsule members do not belong to one real use");
            } finally { input.release(); released.release(); }
        }
        public PacketBuffer marker() { return new PacketBuffer(Unpooled.wrappedBuffer(marker.clone())); }
        public SPacketSetSlot vanilla() { return decodeSlot(vanilla); }
        public byte[] close() { return close.clone(); }
        public byte[] release() { return release.clone(); }
        public String digest() {
            try {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                for (byte[] bytes : Arrays.asList(marker, vanilla, close, release)) hash.update(bytes);
                StringBuilder text = new StringBuilder(); for (byte value : hash.digest()) text.append(String.format("%02x", value & 255));
                return text.toString();
            } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        }
    }
}
