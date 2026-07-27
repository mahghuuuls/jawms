package com.mahghuuuls.jawms.internal.mana;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaStateMessageTest {

    @Test
    void codecPreservesAValidState() {
        ManaStateMessage encoded = new ManaStateMessage(123, 1000, 42);
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        ManaStateMessage decoded = new ManaStateMessage();

        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(123, decoded.getCurrentMana());
        assertEquals(1000, decoded.getMaximumMana());
        assertEquals(42L, decoded.getRevision());
    }

    @Test
    void rejectsImpossibleStates() {
        assertFalse(new ManaStateMessage(-1, 1000, 1).isValid());
        assertFalse(new ManaStateMessage(1001, 1000, 1).isValid());
        assertFalse(new ManaStateMessage(0, 0, 1).isValid());
        assertFalse(new ManaStateMessage(0, 1000, -1).isValid());
    }
}
