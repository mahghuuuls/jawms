package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ActiveStackCollectorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void defaultWizardryArmorProfileProducesApprovedFullSetStats() {
        ManaContribution helmet = ActiveStackCollector.generatedWizardryArmor(
                EntityEquipmentSlot.HEAD,
                WizardryArmorProfile.DEFAULT
        );
        ManaContribution chest = ActiveStackCollector.generatedWizardryArmor(
                EntityEquipmentSlot.CHEST,
                WizardryArmorProfile.DEFAULT
        );
        ManaContribution legs = ActiveStackCollector.generatedWizardryArmor(
                EntityEquipmentSlot.LEGS,
                WizardryArmorProfile.DEFAULT
        );
        ManaContribution boots = ActiveStackCollector.generatedWizardryArmor(
                EntityEquipmentSlot.FEET,
                WizardryArmorProfile.DEFAULT
        );

        ResolvedManaProfile profile = ManaProfileResolver.resolve(
                60,
                2.4D,
                2.0D,
                Arrays.asList(helmet, chest, legs, boots)
        );

        assertEquals(100, profile.getMaximumMana());
        assertEquals(4.0D, profile.getRegeneration(), 0.000000001D);
        assertEquals(0.0D, helmet.getMaximumManaIncrease());
        assertEquals(0.0D, helmet.getGlobalSpellEfficiency());
    }

    @Test
    void disabledProfileGeneratesNothing() {
        WizardryArmorProfile disabled = new WizardryArmorProfile(
                false,
                15,
                0.5D,
                15,
                0.5D,
                10,
                0.5D,
                10,
                0.5D
        );
        assertNull(ActiveStackCollector.generatedWizardryArmor(
                EntityEquipmentSlot.HEAD,
                disabled
        ));
    }

    @Test
    void explicitAndGeneratedLayersSuppressApiProviderEvaluation() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.registerItem(new net.minecraft.util.ResourceLocation("test", "provider"),
                Items.STICK, (player, context) -> {
                    calls.incrementAndGet();
                    return ManaContribution.EMPTY;
                });
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(1L);
        ItemStack stack = new ItemStack(Items.STICK);
        GameplayRules rules = GameplayRules.initialDefaults();
        List<ManaContribution> contributions = new ArrayList<>();
        ConfiguredItemRule explicit = new ConfiguredItemRule(
                Items.STICK.getRegistryName(), ConfiguredItemRule.WILDCARD_METADATA,
                ManaContribution.builder().flatMaximumMana(4).build()
        );

        ActiveStackCollector.collectResolvedStack(
                player, stack, EntityEquipmentSlot.MAINHAND, explicit, null,
                rules, contributions, registry
        );
        ActiveStackCollector.collectResolvedStack(
                player, stack, EntityEquipmentSlot.MAINHAND, null,
                ManaContribution.builder().flatMaximumMana(5).build(),
                rules, contributions, registry
        );

        assertEquals(0, calls.get());
        assertEquals(Arrays.asList(4, 5), Arrays.asList(
                contributions.get(0).getFlatMaximumMana(),
                contributions.get(1).getFlatMaximumMana()
        ));
    }

    @Test
    void configuredLayerAndOverriddenProviderShareOneSourceAwareDecision() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.registerItem(new net.minecraft.util.ResourceLocation("test", "provider"),
                Items.STICK, (player, context) -> {
                    calls.incrementAndGet();
                    return ManaContribution.builder().flatMaximumMana(99).build();
                });
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(1L);
        ConfiguredItemRule explicit = new ConfiguredItemRule(
                Items.STICK.getRegistryName(), ConfiguredItemRule.WILDCARD_METADATA,
                ManaContribution.builder().flatMaximumMana(4).build()
        );
        List<ContributionEvaluationRecord> records = new ArrayList<>();

        ActiveStackCollector.collectResolvedStackRecords(
                player,
                new ManaItemContext(
                        new ItemStack(Items.STICK), ManaSlotCategory.MAIN_HAND, 0
                ),
                explicit,
                null,
                GameplayRules.initialDefaults(),
                records,
                registry
        );

        assertEquals(2, records.size());
        assertEquals(ManaContributionSourceKind.CONFIGURED_ITEM,
                records.get(0).toPublicExplanation().getSourceKind());
        assertEquals(ManaContributionDisposition.APPLIED,
                records.get(0).getDisposition());
        assertEquals(4, records.get(0).getContribution().getFlatMaximumMana());
        assertEquals(ManaContributionSourceKind.ITEM_PROVIDER,
                records.get(1).toPublicExplanation().getSourceKind());
        assertEquals(ManaContributionDisposition.OVERRIDDEN,
                records.get(1).getDisposition());
        assertEquals(0, calls.get());
    }

    @Test
    void generatedLayerAlsoRecordsMatchingProviderAsOverridden() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.registerItem(new net.minecraft.util.ResourceLocation("test", "provider"),
                Items.STICK, (player, context) -> {
                    calls.incrementAndGet();
                    return ManaContribution.EMPTY;
                });
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(1L);
        List<ContributionEvaluationRecord> records = new ArrayList<>();

        ActiveStackCollector.collectResolvedStackRecords(
                player,
                new ManaItemContext(
                        new ItemStack(Items.STICK), ManaSlotCategory.ARMOR, 0
                ),
                null,
                ManaContribution.builder().flatMaximumMana(5).build(),
                GameplayRules.initialDefaults(),
                records,
                registry
        );

        assertEquals(2, records.size());
        assertEquals(ManaContributionSourceKind.GENERATED_WIZARDRY_ARMOR,
                records.get(0).toPublicExplanation().getSourceKind());
        assertEquals(ManaContributionDisposition.APPLIED,
                records.get(0).getDisposition());
        assertEquals(ManaContributionDisposition.OVERRIDDEN,
                records.get(1).getDisposition());
        assertEquals(0, calls.get());
    }

    @Test
    void neutralConfiguredAndGeneratedLayersAreEmptyAndStillOverrideProviders() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.registerItem(new net.minecraft.util.ResourceLocation("test", "provider"),
                Items.STICK, (player, context) -> {
                    calls.incrementAndGet();
                    return ManaContribution.builder().flatMaximumMana(99).build();
                });
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(1L);
        ManaItemContext context = new ManaItemContext(
                new ItemStack(Items.STICK), ManaSlotCategory.ARMOR, 0
        );
        ConfiguredItemRule configured = new ConfiguredItemRule(
                Items.STICK.getRegistryName(), ConfiguredItemRule.WILDCARD_METADATA,
                ManaContribution.EMPTY
        );

        List<ContributionEvaluationRecord> configuredRecords = new ArrayList<>();
        ActiveStackCollector.collectResolvedStackRecords(
                player, context, configured, null, GameplayRules.initialDefaults(),
                configuredRecords, registry
        );
        List<ContributionEvaluationRecord> generatedRecords = new ArrayList<>();
        ActiveStackCollector.collectResolvedStackRecords(
                player, context, null, ManaContribution.EMPTY,
                GameplayRules.initialDefaults(), generatedRecords, registry
        );

        assertEquals(ManaContributionDisposition.EMPTY,
                configuredRecords.get(0).getDisposition());
        assertNull(configuredRecords.get(0).getContribution());
        assertEquals(ManaContributionDisposition.OVERRIDDEN,
                configuredRecords.get(1).getDisposition());
        assertEquals(ManaContributionDisposition.EMPTY,
                generatedRecords.get(0).getDisposition());
        assertNull(generatedRecords.get(0).getContribution());
        assertEquals(ManaContributionDisposition.OVERRIDDEN,
                generatedRecords.get(1).getDisposition());
        assertEquals(0, calls.get());
    }

    @Test
    void sageNonmatchingBonusRequiresEveryPieceToUseGeneratedLayer() {
        EntityPlayer player = mock(EntityPlayerMP.class);
        java.util.EnumMap<EntityEquipmentSlot, ItemStack> equipped =
                new java.util.EnumMap<>(EntityEquipmentSlot.class);
        for (EntityEquipmentSlot slot : Arrays.asList(
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET)) {
            equipped.put(slot, new ItemStack(Items.STICK));
        }
        when(player.getItemStackFromSlot(any(EntityEquipmentSlot.class)))
                .thenAnswer(invocation -> equipped.get(invocation.getArgument(0)));

        ItemStack chest = equipped.get(EntityEquipmentSlot.CHEST);
        assertEquals(true, ActiveStackCollector.allArmorUsesGeneratedLayer(
                player, stack -> true
        ));
        assertEquals(false, ActiveStackCollector.allArmorUsesGeneratedLayer(
                player, stack -> stack != chest
        ));
        assertEquals(10.0D, com.mahghuuuls.jawms.internal.rules
                .WizardryArmorEfficiencyProfile.DEFAULT.getWizard());
        assertEquals(20.0D, com.mahghuuuls.jawms.internal.rules
                .WizardryArmorEfficiencyProfile.DEFAULT.getSage());
        assertEquals(5.0D, com.mahghuuuls.jawms.internal.rules
                .WizardryArmorEfficiencyProfile.DEFAULT.getBattlemage());
        assertEquals(10.0D, com.mahghuuuls.jawms.internal.rules
                .WizardryArmorEfficiencyProfile.DEFAULT.getWarlock());
    }

    @Test
    void configuredBaubleUsesExplicitLayerOnceForItsOccupiedSlot() {
        EntityPlayer player = mock(EntityPlayerMP.class);
        ItemStack stack = new ItemStack(Items.STICK, 64);
        ConfiguredItemRule explicit = new ConfiguredItemRule(
                Items.STICK.getRegistryName(), ConfiguredItemRule.WILDCARD_METADATA,
                ManaContribution.builder().flatMaximumMana(3).build()
        );
        List<ManaContribution> contributions = new ArrayList<>();

        ActiveStackCollector.collectResolvedStack(
                player,
                new ManaItemContext(stack, ManaSlotCategory.BAUBLES, 5),
                explicit,
                null,
                GameplayRules.initialDefaults(),
                contributions,
                new ManaProviderRegistry()
        );

        assertEquals(1, contributions.size());
        assertEquals(3, contributions.get(0).getFlatMaximumMana());
    }

    @Test
    void identicalBaublesInSeparateSlotsHaveSeparateProviderCacheKeys() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        List<String> contexts = new ArrayList<>();
        registry.registerItem(new net.minecraft.util.ResourceLocation("test", "bauble_provider"),
                Items.STICK, (player, context) -> {
                    calls.incrementAndGet();
                    contexts.add(context.getSlotCategory() + ":" + context.getSlotIndex());
                    return ManaContribution.builder().flatRegeneration(1.0D).build();
                });
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(20L);
        GameplayRules rules = GameplayRules.initialDefaults();
        ItemStack stack = new ItemStack(Items.STICK);
        List<ManaContribution> contributions = new ArrayList<>();

        for (int slot : new int[]{1, 4}) {
            ManaItemContext context = new ManaItemContext(
                    stack, ManaSlotCategory.BAUBLES, slot
            );
            ActiveStackCollector.collectResolvedStack(
                    player, context, null, null, rules, contributions, registry
            );
            ActiveStackCollector.collectResolvedStack(
                    player, context, null, null, rules, contributions, registry
            );
        }

        assertEquals(2, calls.get());
        assertEquals(Arrays.asList("BAUBLES:1", "BAUBLES:4"), contexts);
        assertEquals(4, contributions.size());
    }
}
