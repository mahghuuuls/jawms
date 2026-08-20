package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
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
                        ManaContribution.builder()
                                .flatMaximumMana(10)
                                .maximumManaIncrease(25)
                                .maximumManaReduction(10)
                                .flatRegeneration(0.5D)
                                .regenerationIncrease(50)
                                .regenerationReduction(20)
                                .flatLockoutSeconds(-0.25D)
                                .lockoutIncrease(15)
                                .lockoutReduction(30)
                                .globalSpellEfficiency(5)
                                .elementSpellEfficiency(Element.FIRE, 12)
                                .globalSpellCostMultiplier(0.5D)
                                .elementSpellCostMultiplier(Element.FIRE, 1.5D)
                                .regenerateDuringPostCastLockout(true)
                                .regenerateDuringContinuousCasting(true)
                                .build()
                )),
                WizardryArmorProfile.DEFAULT
        );
        rules = rules.withWizardryIntegrationRules(WizardryIntegrationRules.defaults(4L));
        rules = rules.withRevisionAndProviderPreviews(9L, Collections.singletonList(
                new ProviderPreviewEntry(
                        new ResourceLocation("example", "stick_provider"),
                        new ResourceLocation("minecraft", "stick"),
                        ManaContribution.builder().flatMaximumMana(3).build(),
                        java.util.Arrays.asList(0, 7)
                )
        ));
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
        ManaContribution contribution = decoded.getItemRules().get(0).getContribution();
        assertEquals(10, contribution.getFlatMaximumMana());
        assertEquals(25.0D, contribution.getMaximumManaIncrease());
        assertEquals(10.0D, contribution.getMaximumManaReduction());
        assertEquals(12.0D, contribution.getElementSpellEfficiency(Element.FIRE));
        assertEquals(0.5D, contribution.getGlobalSpellCostMultiplier());
        assertEquals(1.5D, contribution.getElementSpellCostMultiplier(Element.FIRE));
        assertTrue(contribution.canRegenerateDuringPostCastLockout());
        assertTrue(contribution.canRegenerateDuringContinuousCasting());
        assertEquals(10, decoded.getArmorProfile().getHelmetMaximum());
        assertEquals(100.0D, decoded.getEfficiencyScale());
        assertEquals(20.0D, decoded.getArmorEfficiencyProfile().getSage());
        assertEquals(4L, decoded.getIntegrationRules().getRevision());
        assertEquals(10.0D, decoded.getIntegrationRules().getStorageManaIncreasePerLevel());
        assertTrue(decoded.getIntegrationRules().isFontLockoutPermission());
        assertEquals(1, decoded.getProviderPreviews().size());
        assertEquals(
                new ResourceLocation("example", "stick_provider"),
                decoded.getProviderPreviews().get(0).getProviderId()
        );
        assertEquals(3, decoded.getProviderPreviews().get(0)
                .getContribution().getFlatMaximumMana());
        assertEquals(
                new java.util.TreeSet<>(java.util.Arrays.asList(0, 7)),
                decoded.getProviderPreviews().get(0).getExcludedMetadata()
        );
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

    @Test
    void rejectsOversizedAndNonDeterministicPreviewTables() {
        GameplayRules rules = GameplayRules.initialDefaults()
                .withRevisionAndProviderPreviews(1L, java.util.Arrays.asList(
                        new ProviderPreviewEntry(
                                new ResourceLocation("example", "z"),
                                new ResourceLocation("minecraft", "stick"),
                                ManaContribution.builder().flatMaximumMana(1).build()
                        ),
                        new ProviderPreviewEntry(
                                new ResourceLocation("example", "a"),
                                new ResourceLocation("minecraft", "apple"),
                                ManaContribution.builder().flatMaximumMana(1).build()
                        )
                ));
        assertFalse(new GameplayRulesMessage(rules).isValid());

        ByteBuf oversized = Unpooled.buffer();
        oversized.writeLong(1L);
        oversized.writeInt(60);
        oversized.writeDouble(2.4D);
        oversized.writeLong(40L);
        oversized.writeInt(20);
        oversized.writeInt(50);
        oversized.writeInt(100);
        oversized.writeInt(0);
        oversized.writeInt(0);
        oversized.writeInt(Integer.MAX_VALUE);
        GameplayRulesMessage decoded = new GameplayRulesMessage();
        decoded.fromBytes(oversized);
        assertFalse(decoded.isValid());

        ByteBuf oversizedIdentifier = Unpooled.buffer();
        oversizedIdentifier.writeLong(1L);
        oversizedIdentifier.writeInt(60);
        oversizedIdentifier.writeDouble(2.4D);
        oversizedIdentifier.writeLong(40L);
        oversizedIdentifier.writeInt(20);
        oversizedIdentifier.writeInt(50);
        oversizedIdentifier.writeInt(100);
        oversizedIdentifier.writeInt(0);
        oversizedIdentifier.writeInt(0);
        oversizedIdentifier.writeInt(1);
        StringBuilder identifier = new StringBuilder("fixture:");
        while (identifier.length() <= 256) {
            identifier.append('a');
        }
        ByteBufUtils.writeUTF8String(oversizedIdentifier, identifier.toString());
        GameplayRulesMessage longIdentifier = new GameplayRulesMessage();
        longIdentifier.fromBytes(oversizedIdentifier);
        assertFalse(longIdentifier.isValid());

        java.util.List<Integer> excessiveMetadata = new java.util.ArrayList<>();
        for (int metadata = 0; metadata <= 4096; metadata++) {
            excessiveMetadata.add(metadata);
        }
        GameplayRules excessiveExclusions = GameplayRules.initialDefaults()
                .withRevisionAndProviderPreviews(2L, Collections.singletonList(
                        new ProviderPreviewEntry(
                                new ResourceLocation("example", "metadata"),
                                new ResourceLocation("minecraft", "stick"),
                                ManaContribution.builder().flatMaximumMana(1).build(),
                                excessiveMetadata
                        )
                ));
        assertFalse(new GameplayRulesMessage(excessiveExclusions).isValid());
    }
}
