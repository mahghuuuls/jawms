package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ActiveStackCollector {

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };

    private ActiveStackCollector() {
    }

    public static List<ManaContribution> collect(EntityPlayer player, GameplayRules rules) {
        if (player == null || rules == null) {
            throw new NullPointerException("player and rules must not be null");
        }
        List<ManaContribution> contributions = new ArrayList<>();
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            collectStack(player.getItemStackFromSlot(slot), slot, rules, contributions);
        }
        collectStack(
                player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND),
                EntityEquipmentSlot.MAINHAND,
                rules,
                contributions
        );
        collectStack(
                player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND),
                EntityEquipmentSlot.OFFHAND,
                rules,
                contributions
        );
        return contributions;
    }

    static void collectStack(
            ItemStack stack,
            EntityEquipmentSlot slot,
            GameplayRules rules,
            List<ManaContribution> destination
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ConfiguredItemRule explicit = rules.findConfiguredItemRule(stack);
        if (explicit != null) {
            destination.add(explicit.getContribution());
            return;
        }
        if (slot.getSlotType() != EntityEquipmentSlot.Type.ARMOR
                || !rules.hasGeneratedWizardryArmor(stack)) {
            return;
        }
        ManaContribution generated = generatedWizardryArmor(
                slot,
                rules.getArmorProfile()
        );
        if (generated != null) {
            destination.add(generated);
        }
    }

    public static ManaContribution generatedWizardryArmor(
            EntityEquipmentSlot slot,
            WizardryArmorProfile profile
    ) {
        if (!profile.isEnabled()) {
            return null;
        }
        switch (slot) {
            case HEAD:
                return armorContribution(
                        profile.getHelmetMaximum(),
                        profile.getHelmetRegeneration()
                );
            case CHEST:
                return armorContribution(
                        profile.getChestMaximum(),
                        profile.getChestRegeneration()
                );
            case LEGS:
                return armorContribution(
                        profile.getLegsMaximum(),
                        profile.getLegsRegeneration()
                );
            case FEET:
                return armorContribution(
                        profile.getBootsMaximum(),
                        profile.getBootsRegeneration()
                );
            default:
                return null;
        }
    }

    private static ManaContribution armorContribution(int maximum, double regeneration) {
        return ManaContribution.builder()
                .flatMaximumMana(maximum)
                .flatRegeneration(regeneration)
                .build();
    }
}
