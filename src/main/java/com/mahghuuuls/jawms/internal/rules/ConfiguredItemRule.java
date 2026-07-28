package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

public final class ConfiguredItemRule {

    public static final int WILDCARD_METADATA = -1;

    private final ResourceLocation itemId;
    private final int metadata;
    private final int maximumMana;
    private final double flatRegeneration;
    private final double percentageRegeneration;

    public ConfiguredItemRule(
            ResourceLocation itemId,
            int metadata,
            int maximumMana,
            double flatRegeneration,
            double percentageRegeneration
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        if (metadata < WILDCARD_METADATA || metadata > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Metadata must be wildcard (-1) or 0 through 32767.");
        }
        requireFinite(flatRegeneration, "flatRegeneration");
        requireFinite(percentageRegeneration, "percentageRegeneration");
        this.metadata = metadata;
        this.maximumMana = maximumMana;
        this.flatRegeneration = flatRegeneration;
        this.percentageRegeneration = percentageRegeneration;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getMetadata() {
        return metadata;
    }

    public int getMaximumMana() {
        return maximumMana;
    }

    public double getFlatRegeneration() {
        return flatRegeneration;
    }

    public double getPercentageRegeneration() {
        return percentageRegeneration;
    }

    public String key() {
        return itemId + "@" + (metadata == WILDCARD_METADATA ? "*" : metadata);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }
}
