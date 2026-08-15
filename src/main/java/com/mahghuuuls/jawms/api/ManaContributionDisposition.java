package com.mahghuuuls.jawms.api;

/** Outcome of considering one source during an accepted profile evaluation. */
public enum ManaContributionDisposition {
    /** The contribution participated in authoritative arithmetic. */
    APPLIED,
    /** Evaluation succeeded with a neutral contribution. */
    EMPTY,
    /** Server rules disabled the relevant provider before invocation. */
    DISABLED,
    /** A higher-precedence configured or generated item layer was selected. */
    OVERRIDDEN,
    /** A provider returned null or a structurally invalid contribution. */
    INVALID,
    /** A provider callback threw a runtime exception. */
    FAILED
}
