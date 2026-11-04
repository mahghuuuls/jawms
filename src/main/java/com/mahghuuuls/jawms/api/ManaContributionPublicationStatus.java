package com.mahghuuuls.jawms.api;

/** Outcome of submitting a full provider contribution. */
public enum ManaContributionPublicationStatus {
    INITIAL_RECORDED,
    UPDATE_RECORDED,
    ALREADY_COMPLETED,
    INITIAL_REQUIRED,
    REVISION_MISMATCH,
    STALE_GENERATION,
    INAPPLICABLE,
    BUSY
}
