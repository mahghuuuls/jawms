package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;

/** Bounded original-source metadata. Contains no stack, spell callback or payment authority. */
public final class WandPublicationRecord {
    public static final int VERSION = 2;
    public static final int MAX_BYTES = 256;
    public static final int MAX_PENDING = 256;
    public static final int RETIRE = 0, SLOT = 1, WINDOW = 2;
    public final UUID epoch;
    public final long serial;
    public final int kind, window;
    private final Entry[] entries;

    public WandPublicationRecord(UUID epoch, long serial, int kind, int window, Entry... entries) {
        this.epoch = Objects.requireNonNull(epoch, "epoch");
        if (serial <= 0 || kind < RETIRE || kind > WINDOW || window < -2 || window > 255
                || entries.length < 1 || entries.length > 2 || (kind == SLOT && entries.length != 1)
                || (kind == RETIRE && window != -1) || (kind == WINDOW && window < 0))
            throw new IllegalArgumentException("Publication record shape");
        this.serial = serial; this.kind = kind; this.window = window; this.entries = entries.clone();
        for (int i = 0; i < this.entries.length; i++) {
            Entry e = Objects.requireNonNull(this.entries[i], "entry");
            if ((kind == RETIRE) != (e.windowSlot == -1)) throw new IllegalArgumentException("Publication slot");
            for (int j = 0; j < i; j++) {
                if (this.entries[j].inventorySlot == e.inventorySlot
                        || (kind != RETIRE && this.entries[j].windowSlot == e.windowSlot))
                    throw new IllegalArgumentException("Duplicate publication mapping");
            }
        }
    }

    public Entry[] entries() { return entries.clone(); }
    public void write(ByteBuf out) {
        out.writeByte(VERSION).writeLong(epoch.getMostSignificantBits()).writeLong(epoch.getLeastSignificantBits());
        out.writeLong(serial).writeByte(kind).writeInt(window).writeByte(entries.length);
        for (Entry e : entries) out.writeByte(e.inventorySlot).writeInt(e.windowSlot)
                .writeLong(e.generation).writeLong(e.intent).writeLong(e.use);
    }
    public static WandPublicationRecord read(ByteBuf in) {
        if (in.readableBytes() > MAX_BYTES || in.readUnsignedByte() != VERSION)
            throw new IllegalArgumentException("Publication metadata version/size");
        UUID epoch = new UUID(in.readLong(), in.readLong());
        long serial = in.readLong(); int kind = in.readUnsignedByte(), window = in.readInt();
        int count = in.readUnsignedByte();
        if (count < 1 || count > 2) throw new IllegalArgumentException("Publication entry count");
        Entry[] entries = new Entry[count];
        for (int i = 0; i < count; i++) entries[i] = new Entry(in.readUnsignedByte(), in.readInt(),
                in.readLong(), in.readLong(), in.readLong());
        if (in.isReadable()) throw new IllegalArgumentException("Trailing publication metadata");
        return new WandPublicationRecord(epoch, serial, kind, window, entries);
    }

    public static final class Entry {
        public final int inventorySlot, windowSlot;
        public final long generation, intent, use;
        public Entry(int inventorySlot, int windowSlot, long generation, long intent, long use) {
            if (!((inventorySlot >= 0 && inventorySlot < 9) || inventorySlot == 40)
                    || windowSlot < -1 || windowSlot > 32767 || generation <= 0 || intent <= 0 || use <= 0)
                throw new IllegalArgumentException("Publication binding");
            this.inventorySlot = inventorySlot; this.windowSlot = windowSlot;
            this.generation = generation; this.intent = intent; this.use = use;
        }
    }
}
