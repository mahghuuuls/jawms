package com.mahghuuuls.jawms.api;

/** Public classification of a registered modifier provider. */
public enum ManaProviderKind {
    /** A provider evaluated for equipped or held stacks of one target item. */
    ITEM,
    /** A player-wide provider evaluated without a target item. */
    CONTEXTUAL
}
