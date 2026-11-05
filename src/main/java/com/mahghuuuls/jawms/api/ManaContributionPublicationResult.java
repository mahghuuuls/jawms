package com.mahghuuuls.jawms.api;

import java.util.Objects;
import java.util.OptionalLong;

/** A recorded revision is a receipt for publication or suspension, distinct from acceptance. */
public final class ManaContributionPublicationResult {
    private final ManaContributionPublicationStatus status;
    private final OptionalLong revision;

    public ManaContributionPublicationResult(ManaContributionPublicationStatus status, OptionalLong revision) {
        this.status = Objects.requireNonNull(status, "status");
        this.revision = Objects.requireNonNull(revision, "revision");
        boolean recorded = status == ManaContributionPublicationStatus.INITIAL_RECORDED
                || status == ManaContributionPublicationStatus.UPDATE_RECORDED
                || status == ManaContributionPublicationStatus.SUSPENDED;
        if (recorded != revision.isPresent() || (recorded && revision.getAsLong() < 1)) {
            throw new IllegalArgumentException("Publication status and revision disagree.");
        }
    }

    public ManaContributionPublicationStatus getStatus() {
        return status;
    }

    public OptionalLong getRevision() {
        return revision;
    }
}
