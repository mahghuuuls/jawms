package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import net.minecraft.util.ResourceLocation;

/** Internal immutable provenance retained from one normal profile evaluation. */
public final class ContributionEvaluationRecord {

    private final ResourceLocation sourceId;
    private final ManaContributionSourceKind sourceKind;
    private final ManaContributionDisposition disposition;
    private final ManaContributionFailure failure;
    private final ResourceLocation itemId;
    private final ManaSlotCategory slotCategory;
    private final int slotIndex;
    private final ManaContribution contribution;

    private ContributionEvaluationRecord(
            ResourceLocation sourceId,
            ManaContributionSourceKind sourceKind,
            ManaContributionDisposition disposition,
            ManaContributionFailure failure,
            ResourceLocation itemId,
            ManaSlotCategory slotCategory,
            int slotIndex,
            ManaContribution contribution
    ) {
        this.sourceId = sourceId;
        this.sourceKind = sourceKind;
        this.disposition = disposition;
        this.failure = failure;
        this.itemId = itemId;
        this.slotCategory = slotCategory;
        this.slotIndex = slotIndex;
        this.contribution = contribution;
    }

    public static ContributionEvaluationRecord playerWide(
            ResourceLocation sourceId,
            ManaContributionSourceKind sourceKind,
            ManaContributionDisposition disposition,
            ManaContributionFailure failure,
            ManaContribution contribution
    ) {
        return new ContributionEvaluationRecord(
                sourceId, sourceKind, disposition, failure, null, null, -1, contribution
        );
    }

    public static ContributionEvaluationRecord item(
            ResourceLocation sourceId,
            ManaContributionSourceKind sourceKind,
            ManaContributionDisposition disposition,
            ManaContributionFailure failure,
            ResourceLocation itemId,
            ManaItemContext context,
            ManaContribution contribution
    ) {
        return new ContributionEvaluationRecord(
                sourceId, sourceKind, disposition, failure, itemId,
                context.getSlotCategory(), context.getSlotIndex(), contribution
        );
    }

    public ManaContributionDisposition getDisposition() {
        return disposition;
    }

    public ManaContribution getContribution() {
        return contribution;
    }

    public ManaContributionExplanation toPublicExplanation() {
        return new ManaContributionExplanation(
                sourceId, sourceKind, disposition, failure, itemId,
                slotCategory, slotIndex, contribution
        );
    }
}
