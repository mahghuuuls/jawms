package com.mahghuuuls.jawms.api;

/** Stable category of a source considered during profile evaluation. */
public enum ManaContributionSourceKind {
    /** An explicit item rule from JAWMS gameplay configuration. */
    CONFIGURED_ITEM,
    /** JAWMS's generated defaults for Wizardry armor. */
    GENERATED_WIZARDRY_ARMOR,
    /** An addon item-provider registration. */
    ITEM_PROVIDER,
    /** An addon player-wide provider registration. */
    CONTEXTUAL_PROVIDER,
    /** A built-in passive adapted from retained Wizardry mechanics. */
    WIZARDRY_PASSIVE
}
