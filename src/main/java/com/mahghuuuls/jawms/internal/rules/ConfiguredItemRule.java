package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

public final class ConfiguredItemRule {

    public static final int WILDCARD_METADATA = -1;

    private final ResourceLocation itemId;
    private final int metadata;
    private final ManaContribution contribution;

    public ConfiguredItemRule(
            ResourceLocation itemId,
            int metadata,
            int maximumMana,
            double flatRegeneration,
            double percentageRegeneration
    ) {
        this(
                itemId,
                metadata,
                ManaContribution.builder()
                        .flatMaximumMana(maximumMana)
                        .flatRegeneration(flatRegeneration)
                        .regenerationIncrease(Math.max(0.0D, percentageRegeneration))
                        .regenerationReduction(Math.max(0.0D, -percentageRegeneration))
                        .build()
        );
    }

    public ConfiguredItemRule(
            ResourceLocation itemId,
            int metadata,
            ManaContribution contribution
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        if (metadata < WILDCARD_METADATA || metadata > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Metadata must be wildcard (-1) or 0 through 32767.");
        }
        this.metadata = metadata;
        this.contribution = Objects.requireNonNull(contribution, "contribution");
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getMetadata() {
        return metadata;
    }

    public int getMaximumMana() {
        return contribution.getFlatMaximumMana();
    }

    public double getFlatRegeneration() {
        return contribution.getFlatRegeneration();
    }

    public double getPercentageRegeneration() {
        return contribution.getRegenerationIncrease() - contribution.getRegenerationReduction();
    }

    public ManaContribution getContribution() {
        return contribution;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null
                || !itemId.equals(stack.getItem().getRegistryName())) {
            return false;
        }
        return metadata == WILDCARD_METADATA || metadata == stack.getMetadata();
    }

    public String key() {
        return itemId + "@" + (metadata == WILDCARD_METADATA ? "*" : metadata);
    }

}
