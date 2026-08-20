package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.util.ResourceLocation;

/** One internal item-wide presentation declaration before rules filtering. */
public final class ProviderPreviewDeclaration {

    private final ResourceLocation providerId;
    private final ResourceLocation targetItemId;
    private final ManaContribution contribution;

    ProviderPreviewDeclaration(
            ResourceLocation providerId,
            ResourceLocation targetItemId,
            ManaContribution contribution
    ) {
        this.providerId = providerId;
        this.targetItemId = targetItemId;
        this.contribution = contribution;
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
}
