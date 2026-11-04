package com.mahghuuuls.jawms.api;

/**
 * Stable reason for an unsuccessful accepted mana mutation.
 *
 * <p>Invalid arguments, wrong-side/thread use, provider-evaluation re-entry,
 * and reservation conflicts remain programming errors reported as exceptions;
 * they are not mutation outcomes.</p>
 */
public enum ManaMutationFailure {
    /** The mutation succeeded, including a successful bounded no-op. */
    NONE,

    /** An exact consumption request exceeded the player's available mana. */
    INSUFFICIENT_MANA,

    /** Legacy or otherwise unspecified rejection. */
    REJECTED,
    /** No authoritative mana profile is available for the current lifecycle. */
    PROFILE_NOT_READY
}
