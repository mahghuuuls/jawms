package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewRegistry;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/** Immutable accepted provider preview transported with one gameplay-rules revision. */
public final class ProviderPreviewEntry {

    private final ResourceLocation providerId;
    private final ResourceLocation targetItemId;
    private final ManaContribution contribution;
    private final SortedSet<Integer> excludedMetadata;

    public ProviderPreviewEntry(
            ResourceLocation providerId,
            ResourceLocation targetItemId,
            ManaContribution contribution
    ) {
        this(providerId, targetItemId, contribution, Collections.emptySet());
    }

    public ProviderPreviewEntry(
            ResourceLocation providerId,
            ResourceLocation targetItemId,
            ManaContribution contribution,
            Collection<Integer> excludedMetadata
    ) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.targetItemId = Objects.requireNonNull(targetItemId, "targetItemId");
        this.contribution = Objects.requireNonNull(contribution, "contribution");
        Objects.requireNonNull(excludedMetadata, "excludedMetadata");
        ProviderPreviewRegistry.validateContribution(contribution);
        if (contribution.isEmpty()) {
            throw new IllegalArgumentException("An accepted provider preview must be non-neutral.");
        }
        TreeSet<Integer> copiedMetadata = new TreeSet<>();
        for (Integer metadata : excludedMetadata) {
            if (metadata == null || metadata < 0 || metadata > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Excluded metadata must be 0 through 32767."
                );
            }
            copiedMetadata.add(metadata);
        }
        this.excludedMetadata = Collections.unmodifiableSortedSet(copiedMetadata);
    }

    public ResourceLocation getProviderId() {
        return providerId;
    }

    public ResourceLocation getTargetItemId() {
        return targetItemId;
    }

    public ManaContribution getContribution() {
        return contribution;
    }

    public SortedSet<Integer> getExcludedMetadata() {
        return excludedMetadata;
    }

    public boolean appliesToMetadata(int metadata) {
        return !excludedMetadata.contains(metadata);
    }

}
