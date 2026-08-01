package com.mahghuuuls.jawms.internal.mana;

import electroblob.wizardry.constants.Element;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaStateMessageTest {

    @Test
    void codecPreservesAValidState() {
        EnumMap<Element, Double> multipliers = new EnumMap<>(Element.class);
        multipliers.put(Element.FIRE, 1.5D);
        ManaStateMessage encoded = new ManaStateMessage(
                23, 50, 2.5D, 42, 10.0D, Collections.emptyMap(),
                0.5D, multipliers
        );
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        ManaStateMessage decoded = new ManaStateMessage();

        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(23, decoded.getCurrentMana());
        assertEquals(50, decoded.getMaximumMana());
        assertEquals(2.5D, decoded.getEffectiveRegeneration());
        assertEquals(42L, decoded.getRevision());
        assertEquals(0.5D, decoded.getGlobalSpellCostMultiplier());
        assertEquals(1.5D, decoded.getElementSpellCostMultipliers().get(Element.FIRE));
    }

    @Test
    void rejectsImpossibleStates() {
        assertFalse(new ManaStateMessage(-1, 50, 2.0D, 1).isValid());
        assertFalse(new ManaStateMessage(51, 50, 2.0D, 1).isValid());
        assertFalse(new ManaStateMessage(0, 0, 2.0D, 1).isValid());
        assertFalse(new ManaStateMessage(0, 50, -1.0D, 1).isValid());
        assertFalse(new ManaStateMessage(0, 50, Double.NaN, 1).isValid());
        assertFalse(new ManaStateMessage(0, 50, 2.0D, -1).isValid());
    }
}
