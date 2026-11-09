package com.mahghuuuls.jawms.api;

import net.minecraft.entity.player.EntityPlayer;

/** A read-only check of a recorded contribution at first mana-profile acceptance. */
@FunctionalInterface
public interface ManaFirstAcceptanceVerifier {

    /**
     * Verify the immutable contribution used by this candidate for the exact logical-server
     * player, provider generation and publication revision. An explicit empty group is a
     * complete input. Return true only when the provider can confirm that all normalized
     * values match its current settled group. False or a runtime exception blocks the attempt.
     *
     * <p>This callback runs synchronously on the server thread and may run again after a
     * timeout. Use a bounded local read. Do not publish or suspend, acquire generation
     * tokens, invalidate caches, mutate gameplay state, schedule work or block on I/O.
     * If the complete group changed, publish it against the current revision outside this
     * callback and obtain an {@link ManaContributionPublicationStatus#UPDATE_RECORDED} receipt.
     * Verification of that replacement belongs to a fresh acceptance attempt.
     */
    boolean verify(EntityPlayer player, ManaProfileGeneration generation,
            long publicationRevision, ManaContribution contribution);
}
