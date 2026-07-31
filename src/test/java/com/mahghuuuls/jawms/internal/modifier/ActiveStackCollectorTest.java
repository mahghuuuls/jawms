package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
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
                50,
                2.0D,
                2.0D,
                Arrays.asList(helmet, chest, legs, boots)
        );

        assertEquals(100, profile.getMaximumMana());
        assertEquals(4.0D, profile.getRegeneration());
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
}
