package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActiveStackCollectorTest {

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
}
