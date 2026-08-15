package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.Tags;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorEfficiencyProfile;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemWizardArmour;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

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
        List<ManaContribution> result = new ArrayList<>();
        for (ContributionEvaluationRecord record : collectRecords(player, rules)) {
            if (record.getDisposition() == ManaContributionDisposition.APPLIED) {
                result.add(record.getContribution());
            }
        }
        return result;
    }

    public static List<ContributionEvaluationRecord> collectRecords(
            EntityPlayer player, GameplayRules rules
    ) {
        if (player == null || rules == null) {
            throw new NullPointerException("player and rules must not be null");
        }
        List<ContributionEvaluationRecord> records = new ArrayList<>();
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            collectStackRecords(player, player.getItemStackFromSlot(slot), slot, rules, records);
        }
        collectStackRecords(
                player,
                player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND),
                EntityEquipmentSlot.MAINHAND,
                rules,
                records
        );
        collectStackRecords(
                player,
                player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND),
                EntityEquipmentSlot.OFFHAND,
                rules,
                records
        );
        for (ActiveSlotSource source : ActiveSlotSources.current()) {
            source.collect(player, (stack, category, slotIndex) -> collectStackRecords(
                    player, stack, category, slotIndex, rules, records
            ));
        }
        return records;
    }

    private static void collectStackRecords(
            EntityPlayer player,
            ItemStack stack,
            ManaSlotCategory category,
            int slotIndex,
            GameplayRules rules,
            List<ContributionEvaluationRecord> destination
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        collectResolvedStackRecords(
                player,
                new ManaItemContext(stack, category, slotIndex),
                rules.findConfiguredItemRule(stack),
                null,
                rules,
                destination,
                ManaProviderRegistry.instance()
        );
    }

    private static void collectStackRecords(
            EntityPlayer player,
            ItemStack stack,
            EntityEquipmentSlot slot,
            GameplayRules rules,
            List<ContributionEvaluationRecord> destination
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ConfiguredItemRule explicit = rules.findConfiguredItemRule(stack);
        ManaContribution generated = slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR
                && rules.hasGeneratedWizardryArmor(stack)
                ? generatedWizardryArmor(player, stack, slot, rules)
                : null;
        collectResolvedStackRecords(
                player, context(stack, slot), explicit, generated, rules,
                destination, ManaProviderRegistry.instance()
        );
    }

    static void collectResolvedStackRecords(
            EntityPlayer player,
            ManaItemContext context,
            ConfiguredItemRule explicit,
            ManaContribution generated,
            GameplayRules rules,
            List<ContributionEvaluationRecord> destination,
            ManaProviderRegistry registry
    ) {
        ResourceLocation itemId = context.getStack().getItem().getRegistryName();
        boolean overridden = explicit != null || generated != null;
        if (explicit != null) {
            destination.add(itemSource(
                    configuredSourceId(explicit),
                    ManaContributionSourceKind.CONFIGURED_ITEM,
                    itemId,
                    context,
                    explicit.getContribution()
            ));
        } else if (generated != null) {
            destination.add(itemSource(
                    new ResourceLocation(Tags.MOD_ID, "generated_wizardry_armor"),
                    ManaContributionSourceKind.GENERATED_WIZARDRY_ARMOR,
                    itemId,
                    context,
                    generated
            ));
        }
        destination.addAll(registry.evaluateItemRecords(
                player, context, rules, overridden
        ));
    }

    private static ContributionEvaluationRecord itemSource(
            ResourceLocation sourceId,
            ManaContributionSourceKind sourceKind,
            ResourceLocation itemId,
            ManaItemContext context,
            ManaContribution contribution
    ) {
        boolean empty = contribution.isEmpty();
        return ContributionEvaluationRecord.item(
                sourceId,
                sourceKind,
                empty ? ManaContributionDisposition.EMPTY
                        : ManaContributionDisposition.APPLIED,
                ManaContributionFailure.NONE,
                itemId,
                context,
                empty ? null : contribution
        );
    }

    private static ResourceLocation configuredSourceId(ConfiguredItemRule rule) {
        ResourceLocation itemId = rule.getItemId();
        String metadata = rule.getMetadata() == ConfiguredItemRule.WILDCARD_METADATA
                ? "wildcard" : Integer.toString(rule.getMetadata());
        return new ResourceLocation(
                Tags.MOD_ID,
                "configured_item/" + itemId.getNamespace() + "/" + itemId.getPath()
                        + "/" + metadata
        );
    }

    static void collectStack(
            EntityPlayer player,
            ItemStack stack,
            ManaSlotCategory category,
            int slotIndex,
            GameplayRules rules,
            List<ManaContribution> destination
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ConfiguredItemRule explicit = rules.findConfiguredItemRule(stack);
        collectResolvedStack(
                player,
                new ManaItemContext(stack, category, slotIndex),
                explicit,
                null,
                rules,
                destination,
                ManaProviderRegistry.instance()
        );
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
            generated = generatedWizardryArmor(player, stack, slot, rules);
        }
        collectResolvedStack(player, context(stack, slot), explicit, generated, rules,
                destination, ManaProviderRegistry.instance());
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
        collectResolvedStack(
                player, context(stack, slot), explicit, generated, rules, destination, registry
        );
    }

    static void collectResolvedStack(
            EntityPlayer player,
            ManaItemContext context,
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

    public static ManaContribution generatedWizardryArmor(
            EntityPlayer player, ItemStack stack, EntityEquipmentSlot slot, GameplayRules rules
    ) {
        ManaContribution mana = generatedWizardryArmor(slot, rules.getArmorProfile());
        if (!(stack.getItem() instanceof ItemWizardArmour)) {
            return mana;
        }
        ItemWizardArmour armor = (ItemWizardArmour) stack.getItem();
        WizardryArmorEfficiencyProfile efficiency = rules.getArmorEfficiencyProfile();
        if (!efficiency.isEnabled()) {
            return mana;
        }
        ManaContribution.Builder builder = ManaContribution.builder();
        if (mana != null) {
            builder.flatMaximumMana(mana.getFlatMaximumMana())
                    .flatRegeneration(mana.getFlatRegeneration());
        }
        double points = efficiency.getPerPiece(armor.armourClass);
        Element matchingElement = armor.element == null ? Element.MAGIC : armor.element;
        builder.elementSpellEfficiency(matchingElement, points);
        if (slot == EntityEquipmentSlot.HEAD
                && armor.armourClass == ItemWizardArmour.ArmourClass.SAGE
                && ItemWizardArmour.isWearingFullSet(player, armor.element, armor.armourClass)
                && ItemWizardArmour.doAllArmourPiecesHaveMana(player)
                && allArmorUsesGeneratedLayer(player, rules)) {
            for (Element element : Element.values()) {
                if (element != matchingElement) {
                    builder.elementSpellEfficiency(
                            element, efficiency.getSageNonmatchingFullSet()
                    );
                }
            }
        }
        return builder.build();
    }

    private static boolean allArmorUsesGeneratedLayer(EntityPlayer player, GameplayRules rules) {
        return allArmorUsesGeneratedLayer(player, equipped ->
                rules.hasGeneratedWizardryArmor(equipped)
                        && rules.findConfiguredItemRule(equipped) == null);
    }

    static boolean allArmorUsesGeneratedLayer(
            EntityPlayer player, java.util.function.Predicate<ItemStack> usesGeneratedLayer
    ) {
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = player.getItemStackFromSlot(slot);
            if (!usesGeneratedLayer.test(equipped)) {
                return false;
            }
        }
        return true;
    }

    private static ManaContribution armorContribution(int maximum, double regeneration) {
        return ManaContribution.builder()
                .flatMaximumMana(maximum)
                .flatRegeneration(regeneration)
                .build();
    }
}
