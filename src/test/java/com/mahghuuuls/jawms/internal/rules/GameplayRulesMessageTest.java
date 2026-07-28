package com.mahghuuuls.jawms.internal.rules;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayRulesMessageTest {

    @Test
    void codecPreservesCompleteRulesSnapshot() {
        Map<ResourceLocation, Double> costs = new LinkedHashMap<>();
        costs.put(new ResourceLocation("ebwizardry", "magic_missile"), 5.5D);
        GameplayRules rules = new GameplayRules(
                9L,
                50,
                2.0D,
                40L,
                GameplayRules.DEFAULT_COST_FORMULA,
                20,
                50,
                100,
                costs,
                Collections.singletonList(new ConfiguredItemRule(
                        new ResourceLocation("minecraft", "stick"),
                        ConfiguredItemRule.WILDCARD_METADATA,
                        10,
                        0.5D,
                        50.0D
                )),
                WizardryArmorProfile.DEFAULT
        );
        GameplayRulesMessage encoded = new GameplayRulesMessage(rules);
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        GameplayRulesMessage decoded = new GameplayRulesMessage();

        decoded.fromBytes(buffer);

        assertTrue(decoded.isValid());
        assertEquals(9L, decoded.getRevision());
        assertEquals(50, decoded.getBaseMaximum());
        assertEquals(2.0D, decoded.getBaseRegeneration());
        assertEquals(40L, decoded.getLockoutTicks());
        assertEquals(5.5D, decoded.getSpellCosts().get(
                new ResourceLocation("ebwizardry", "magic_missile")
        ));
        assertEquals(1, decoded.getItemRules().size());
        assertEquals(15, decoded.getArmorProfile().getHelmetMaximum());
    }

    @Test
    void rejectsMalformedOrNumericallyInvalidRules() {
        ByteBuf malformed = Unpooled.buffer();
        malformed.writeLong(1L);
        malformed.writeInt(50);
        malformed.writeDouble(2.0D);
        malformed.writeLong(40L);
        malformed.writeInt(20);
        malformed.writeInt(50);
        malformed.writeInt(100);
        malformed.writeInt(Integer.MAX_VALUE);
        GameplayRulesMessage malformedMessage = new GameplayRulesMessage();
        malformedMessage.fromBytes(malformed);
        assertFalse(malformedMessage.isValid());

        GameplayRules valid = GameplayRules.initialDefaults();
        GameplayRulesMessage validMessage = new GameplayRulesMessage(valid);
        assertTrue(validMessage.isValid());
    }
}
