package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import electroblob.wizardry.constants.Element;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketSizeTest {

    @Test
    void fullManaStatePayloadRemainsBelow256Bytes() {
        EnumMap<Element, Double> efficiency = new EnumMap<>(Element.class);
        EnumMap<Element, Double> multipliers = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            efficiency.put(element, 40.0D);
            multipliers.put(element, 0.5D);
        }

        ByteBuf buffer = Unpooled.buffer();
        try {
            new ManaStateMessage(
                    50, 100, 4.0D, 42L,
                    40.0D, efficiency, 0.5D, multipliers
            ).toBytes(buffer);
            assertTrue(buffer.readableBytes() <= 256, "mana state payload exceeded 256 bytes");
        } finally {
            buffer.release();
        }
    }

    @Test
    void representativePopulatedRulesPayloadRemainsBelow16KiB() throws Exception {
        Map<ResourceLocation, Double> spellCosts = new LinkedHashMap<>();
        for (int index = 0; index < 64; index++) {
            spellCosts.put(new ResourceLocation("ebwizardry", "representative_spell_" + index), 10.0D + index);
        }
        List<ConfiguredItemRule> itemRules = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            itemRules.add(new ConfiguredItemRule(
                    new ResourceLocation("exampleaddon", "representative_item_" + index),
                    ConfiguredItemRule.WILDCARD_METADATA,
                    10, 0.5D, 25.0D
            ));
        }
        GameplayRules rules = new GameplayRules(
                7L, 50, 2.0D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, spellCosts, itemRules, WizardryArmorProfile.DEFAULT
        );
        Set<ResourceLocation> armorIds = new LinkedHashSet<>();
        for (int index = 0; index < 128; index++) {
            armorIds.add(new ResourceLocation("ebwizardry", "representative_armor_" + index));
        }
        setGeneratedArmorIds(rules, armorIds);

        ByteBuf buffer = Unpooled.buffer();
        try {
            new GameplayRulesMessage(rules).toBytes(buffer);
            assertTrue(buffer.readableBytes() <= 16 * 1024,
                    "representative populated rules payload exceeded 16 KiB");
        } finally {
            buffer.release();
        }
    }

    private static void setGeneratedArmorIds(
            GameplayRules rules,
            Set<ResourceLocation> armorIds
    ) throws Exception {
        Field field = GameplayRules.class.getDeclaredField("generatedWizardryArmorIds");
        field.setAccessible(true);
        field.set(rules, Collections.unmodifiableSet(new LinkedHashSet<>(armorIds)));
    }
}
