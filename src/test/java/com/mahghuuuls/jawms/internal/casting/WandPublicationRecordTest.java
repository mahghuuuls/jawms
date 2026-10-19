package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WandPublicationRecordTest {
    private final UUID epoch = UUID.randomUUID();
    private WandPublicationRecord.Entry entry(int slot, int window) {
        return new WandPublicationRecord.Entry(slot, window, 3, 4, 5);
    }
    @Test void twoMappedBindingsRoundTripWithoutStackOrSpellPayload() {
        WandPublicationRecord original = new WandPublicationRecord(epoch, 7, WandPublicationRecord.WINDOW, 0,
                entry(0, 36), entry(40, 45));
        ByteBuf wire = Unpooled.buffer();
        try {
            original.write(wire);
            assertTrue(wire.readableBytes() <= WandPublicationRecord.MAX_BYTES);
            WandPublicationRecord read = WandPublicationRecord.read(wire);
            assertEquals(epoch, read.epoch); assertEquals(7, read.serial);
            assertEquals(2, read.entries().length); assertEquals(45, read.entries()[1].windowSlot);
            assertEquals(40, read.entries()[1].inventorySlot); assertEquals(3, read.entries()[1].generation);
            WandPublicationRecord.Entry[] leaked = read.entries(); leaked[0] = null;
            assertNotNull(read.entries()[0]);
        } finally { wire.release(); }
    }
    @Test void everyTruncationAndTrailingOrOversizedOrOldVersionIsRejected() {
        ByteBuf complete = Unpooled.buffer();
        new WandPublicationRecord(epoch, 1, WandPublicationRecord.SLOT, -2, entry(0, 0)).write(complete);
        try {
            for (int length = 0; length < complete.readableBytes(); length++) {
                ByteBuf truncated = complete.copy(0, length);
                try { assertThrows(RuntimeException.class, () -> WandPublicationRecord.read(truncated)); }
                finally { truncated.release(); }
            }
            ByteBuf trailing = complete.copy();
            try { trailing.writeByte(0); assertThrows(RuntimeException.class, () -> WandPublicationRecord.read(trailing)); }
            finally { trailing.release(); }
            ByteBuf old = complete.copy();
            try { old.setByte(0, 1); assertThrows(RuntimeException.class, () -> WandPublicationRecord.read(old)); }
            finally { old.release(); }
            ByteBuf oversized = Unpooled.buffer().writeZero(WandPublicationRecord.MAX_BYTES + 1);
            try { assertThrows(RuntimeException.class, () -> WandPublicationRecord.read(oversized)); }
            finally { oversized.release(); }
        } finally { complete.release(); }
    }
    @Test void malformedMappingsAndIdentityAndRetirementShapeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord.Entry(9, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord.Entry(0, -2, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord.Entry(0, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord(epoch, 0, 1, 0, entry(0, 36)));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord(epoch, 1, 2, 0, entry(0, 36), entry(0, 37)));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord(epoch, 1, 2, 0, entry(0, 36), entry(1, 36)));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord(epoch, 1, 1, 0, entry(0, 36), entry(1, 37)));
        assertThrows(IllegalArgumentException.class, () -> new WandPublicationRecord(epoch, 1, 0, 0, entry(0, -1)));
        assertDoesNotThrow(() -> new WandPublicationRecord(epoch, 1, 0, -1, entry(0, -1)));
    }
}
