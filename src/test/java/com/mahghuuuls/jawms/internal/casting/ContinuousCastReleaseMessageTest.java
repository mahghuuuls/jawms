package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousCastReleaseMessageTest {

    @Test
    void roundTripPreservesReleaseIdentity() {
        ContinuousCastReleaseMessage encoded = new ContinuousCastReleaseMessage(
                EnumHand.OFF_HAND,
                new ResourceLocation("ebwizardry", "flame_ray")
        );
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);

        ContinuousCastReleaseMessage decoded = new ContinuousCastReleaseMessage();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(EnumHand.OFF_HAND, decoded.getHand());
        assertEquals(
                new ResourceLocation("ebwizardry", "flame_ray"),
                decoded.getSpellId()
        );
    }

    @Test
    void malformedHandIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(255);

        ContinuousCastReleaseMessage decoded = new ContinuousCastReleaseMessage();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isValid());
    }
}
