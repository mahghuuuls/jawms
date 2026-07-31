package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
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
            collectStack(player, player.getItemStackFromSlot(slot), slot, rules, contributions);
        }
        collectStack(
                player,
                player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND),
                EntityEquipmentSlot.MAINHAND,
                rules,
                contributions
        );
        collectStack(
                player,
                player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND),
                EntityEquipmentSlot.OFFHAND,
                rules,
                contributions
        );
        return contributions;
    }

    static void collectStack(
            EntityPlayer player,
            ItemStack stack,
            EntityEquipmentSlot slot,
            GameplayRules rules,
            List<ManaContribution> destination
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ConfiguredItemRule explicit = rules.findConfiguredItemRule(stack);
        ManaContribution generated = null;
        if (slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR
                && rules.hasGeneratedWizardryArmor(stack)) {
            generated = generatedWizardryArmor(slot, rules.getArmorProfile());
        }
        collectResolvedStack(
                player, stack, slot, explicit, generated, rules, destination,
                ManaProviderRegistry.instance()
        );
    }

    static void collectResolvedStack(
            EntityPlayer player,
            ItemStack stack,
            EntityEquipmentSlot slot,
            ConfiguredItemRule explicit,
            ManaContribution generated,
            GameplayRules rules,
            List<ManaContribution> destination,
            ManaProviderRegistry registry
    ) {
        if (explicit != null) {
            destination.add(explicit.getContribution());
            return;
        }
        if (generated != null) {
            destination.add(generated);
            return;
        }
        ManaItemContext context = context(stack, slot);
        destination.addAll(registry.evaluateItem(player, context, rules));
    }

    private static ManaItemContext context(ItemStack stack, EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return new ManaItemContext(stack, ManaSlotCategory.ARMOR, 0);
            case CHEST:
                return new ManaItemContext(stack, ManaSlotCategory.ARMOR, 1);
            case LEGS:
                return new ManaItemContext(stack, ManaSlotCategory.ARMOR, 2);
            case FEET:
                return new ManaItemContext(stack, ManaSlotCategory.ARMOR, 3);
            case MAINHAND:
                return new ManaItemContext(stack, ManaSlotCategory.MAIN_HAND, 0);
            case OFFHAND:
                return new ManaItemContext(stack, ManaSlotCategory.OFF_HAND, 0);
            default:
                throw new IllegalArgumentException("Unsupported active equipment slot: " + slot);
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
