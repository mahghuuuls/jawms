package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousCastStopMessageTest {

    @Test
    void codecPreservesHandAndSpellIdentity() {
        ContinuousCastStopMessage encoded = new ContinuousCastStopMessage(
                EnumHand.OFF_HAND,
                new ResourceLocation("ebwizardry", "flame_ray"),
                true
        );
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        ContinuousCastStopMessage decoded = new ContinuousCastStopMessage();

        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(EnumHand.OFF_HAND, decoded.getHand());
        assertEquals(
                new ResourceLocation("ebwizardry", "flame_ray"),
                decoded.getSpellId()
        );
        assertTrue(decoded.shouldResetImmediately());
    }

    @Test
    void rejectsInvalidHandAndOversizedIdentifier() {
        ByteBuf invalidHand = Unpooled.buffer();
        invalidHand.writeByte(99);
        ByteBufUtils.writeUTF8String(invalidHand, "ebwizardry:flame_ray");
        invalidHand.writeBoolean(true);
        ContinuousCastStopMessage first = new ContinuousCastStopMessage();
        first.fromBytes(invalidHand);
        assertFalse(first.isValid());

        ByteBuf oversizedIdentifier = Unpooled.buffer();
        oversizedIdentifier.writeByte(EnumHand.MAIN_HAND.ordinal());
        ByteBufUtils.writeUTF8String(
                oversizedIdentifier,
                "ebwizardry:" + repeat('a', 257)
        );
        oversizedIdentifier.writeBoolean(false);
        ContinuousCastStopMessage second = new ContinuousCastStopMessage();
        second.fromBytes(oversizedIdentifier);
        assertFalse(second.isValid());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
