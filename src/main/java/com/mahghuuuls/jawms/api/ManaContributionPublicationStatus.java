package com.mahghuuuls.jawms.api;

/** Outcome of publishing or suspending a provider contribution. */
public enum ManaContributionPublicationStatus {
    INITIAL_RECORDED,
    UPDATE_RECORDED,
    ALREADY_COMPLETED,
    INITIAL_REQUIRED,
    REVISION_MISMATCH,
    STALE_GENERATION,
    INAPPLICABLE,
    BUSY,
    SUSPENDED,
    ALREADY_ACCEPTED
}
