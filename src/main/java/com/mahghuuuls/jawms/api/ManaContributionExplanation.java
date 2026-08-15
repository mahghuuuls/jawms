package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Immutable public description of one source considered by an accepted evaluation. */
public final class ManaContributionExplanation {

    private final ResourceLocation sourceId;
    private final ManaContributionSourceKind sourceKind;
    private final ManaContributionDisposition disposition;
    private final ManaContributionFailure failure;
    private final ResourceLocation itemId;
    private final ManaSlotCategory slotCategory;
    private final int slotIndex;
    private final ManaContribution acceptedContribution;

    public ManaContributionExplanation(
            ResourceLocation sourceId,
            ManaContributionSourceKind sourceKind,
            ManaContributionDisposition disposition,
            ManaContributionFailure failure,
            ResourceLocation itemId,
            ManaSlotCategory slotCategory,
            int slotIndex,
            ManaContribution acceptedContribution
    ) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.failure = Objects.requireNonNull(failure, "failure");
        if ((slotCategory == null) != (slotIndex < 0)) {
            throw new IllegalArgumentException(
                    "Slot category and non-negative index must either both be present or absent."
            );
        }
        if (disposition == ManaContributionDisposition.APPLIED
                && acceptedContribution == null) {
            throw new IllegalArgumentException("Applied sources require a contribution.");
        }
        if (disposition == ManaContributionDisposition.APPLIED
                && acceptedContribution.isEmpty()) {
            throw new IllegalArgumentException(
                    "A neutral contribution must use the EMPTY disposition."
            );
        }
        if (disposition != ManaContributionDisposition.APPLIED
                && acceptedContribution != null) {
            throw new IllegalArgumentException(
                    "Only applied sources may expose an accepted contribution."
            );
        }
        boolean invalidFailure = failure == ManaContributionFailure.NULL_RESULT
                || failure == ManaContributionFailure.INVALID_RESULT;
        if ((disposition == ManaContributionDisposition.INVALID && !invalidFailure)
                || (disposition == ManaContributionDisposition.FAILED
                && failure != ManaContributionFailure.PROVIDER_EXCEPTION)
                || (disposition != ManaContributionDisposition.INVALID
                && disposition != ManaContributionDisposition.FAILED
                && failure != ManaContributionFailure.NONE)) {
            throw new IllegalArgumentException("Disposition and failure category disagree.");
        }
        this.itemId = itemId;
        this.slotCategory = slotCategory;
        this.slotIndex = slotIndex;
        this.acceptedContribution = acceptedContribution;
    }

    public ResourceLocation getSourceId() {
        return sourceId;
    }

    public ManaContributionSourceKind getSourceKind() {
        return sourceKind;
    }

    public ManaContributionDisposition getDisposition() {
        return disposition;
    }

    public ManaContributionFailure getFailure() {
        return failure;
    }

    public boolean hasItemId() {
        return itemId != null;
    }

    public ResourceLocation getItemId() {
        if (itemId == null) {
            throw new IllegalStateException("This source has no item identity.");
        }
        return itemId;
    }

    public boolean hasSlot() {
        return slotCategory != null;
    }

    public ManaSlotCategory getSlotCategory() {
        if (slotCategory == null) {
            throw new IllegalStateException("This source has no item slot.");
        }
        return slotCategory;
    }

    public int getSlotIndex() {
        if (slotCategory == null) {
            throw new IllegalStateException("This source has no item slot.");
        }
        return slotIndex;
    }

    public boolean hasAcceptedContribution() {
        return acceptedContribution != null;
    }

    public ManaContribution getAcceptedContribution() {
        if (acceptedContribution == null) {
            throw new IllegalStateException("This source has no accepted contribution.");
        }
        return acceptedContribution;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManaContributionExplanation)) {
            return false;
        }
        ManaContributionExplanation that = (ManaContributionExplanation) other;
        return slotIndex == that.slotIndex
                && sourceId.equals(that.sourceId)
                && sourceKind == that.sourceKind
                && disposition == that.disposition
                && failure == that.failure
                && Objects.equals(itemId, that.itemId)
                && slotCategory == that.slotCategory
                && Objects.equals(acceptedContribution, that.acceptedContribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId, sourceKind, disposition, failure, itemId,
                slotCategory, slotIndex, acceptedContribution);
    }

    @Override
    public String toString() {
        return "ManaContributionExplanation{sourceId=" + sourceId
                + ", sourceKind=" + sourceKind
                + ", disposition=" + disposition
                + ", failure=" + failure
                + ", itemId=" + itemId
                + ", slot=" + (slotCategory == null ? "none"
                : slotCategory + ":" + slotIndex)
                + '}';
    }
}
