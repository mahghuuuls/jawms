package com.mahghuuuls.jawms.api;

/** Bounded failure classification that never exposes a provider exception. */
public enum ManaContributionFailure {
    /** The source has no failure. */
    NONE,
    /** A provider returned null. */
    NULL_RESULT,
    /** A returned contribution failed JAWMS validation. */
    INVALID_RESULT,
    /** A provider callback threw; the exception itself is not retained. */
    PROVIDER_EXCEPTION
}
