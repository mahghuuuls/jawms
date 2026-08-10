package com.mahghuuuls.jawms.api.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHudContextTest {

    @Test
    void knownContextCarriesCapturedRenderValues() {
        ClientHudContext context = ClientHudContext.known(true, 0.25F, 320, 180);

        assertTrue(context.isCreativeModeKnown());
        assertTrue(context.isCreativeMode());
        assertEquals(0.25F, context.getPartialTicks());
        assertEquals(320, context.getScaledWidth());
        assertEquals(180, context.getScaledHeight());
    }

    @Test
    void unknownContextRejectsCreativeModeGuessing() {
        ClientHudContext context = ClientHudContext.unknown(0.75F, 640, 360);

        assertFalse(context.isCreativeModeKnown());
        assertThrows(IllegalStateException.class, context::isCreativeMode);
        assertEquals(0.75F, context.getPartialTicks());
        assertEquals(640, context.getScaledWidth());
        assertEquals(360, context.getScaledHeight());
    }

    @Test
    void factoriesRejectInvalidRenderValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientHudContext.known(false, Float.NaN, 320, 180));
        assertThrows(IllegalArgumentException.class,
                () -> ClientHudContext.unknown(Float.POSITIVE_INFINITY, 320, 180));
        assertThrows(IllegalArgumentException.class,
                () -> ClientHudContext.known(false, 0.0F, -1, 180));
        assertThrows(IllegalArgumentException.class,
                () -> ClientHudContext.unknown(0.0F, 320, -1));
    }
}
