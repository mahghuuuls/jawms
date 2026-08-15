package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Immutable public description of one accepted provider registration. */
public final class ManaProviderDescriptor {

    private final ResourceLocation providerId;
    private final ManaProviderKind kind;
    private final ResourceLocation targetItemId;

    public ManaProviderDescriptor(
            ResourceLocation providerId,
            ManaProviderKind kind,
            ResourceLocation targetItemId
    ) {
        if (providerId == null || kind == null) {
            throw new NullPointerException("Provider ID and kind must not be null.");
        }
        if (kind == ManaProviderKind.CONTEXTUAL && targetItemId != null) {
            throw new IllegalArgumentException(
                    "A contextual provider cannot have a target item ID."
            );
        }
        this.providerId = providerId;
        this.kind = kind;
        this.targetItemId = targetItemId;
    }

    /** Returns the globally unique ID that determines evaluation order. */
    public ResourceLocation getProviderId() {
        return providerId;
    }

    /** Returns whether this registration is item-targeted or contextual. */
    public ManaProviderKind getKind() {
        return kind;
    }

    /**
     * Returns whether an item provider's target registry ID is currently known.
     * Contextual providers always return {@code false}.
     */
    public boolean hasKnownTargetItemId() {
        return kind == ManaProviderKind.ITEM && targetItemId != null;
    }

    /**
     * Returns the immutable target registry ID for an item provider.
     *
     * @throws IllegalStateException for contextual providers or when the item
     *                               has not yet received a registry ID
     */
    public ResourceLocation getTargetItemId() {
        if (kind != ManaProviderKind.ITEM) {
            throw new IllegalStateException("A contextual provider has no target item.");
        }
        if (targetItemId == null) {
            throw new IllegalStateException("The target item's registry ID is unknown.");
        }
        return targetItemId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManaProviderDescriptor)) {
            return false;
        }
        ManaProviderDescriptor that = (ManaProviderDescriptor) other;
        return providerId.equals(that.providerId)
                && kind == that.kind
                && Objects.equals(targetItemId, that.targetItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, kind, targetItemId);
    }

    @Override
    public String toString() {
        return "ManaProviderDescriptor{providerId=" + providerId
                + ", kind=" + kind
                + ", targetItemId=" + (targetItemId == null ? "unknown" : targetItemId)
                + '}';
    }
}
