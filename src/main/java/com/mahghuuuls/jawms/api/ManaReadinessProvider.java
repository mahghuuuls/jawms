package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.internal.mana.ManaService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Optional;

/** Provider-bound, server-thread interface for managing complete mana contributions. */
public final class ManaReadinessProvider {
    private final ResourceLocation id;

    ManaReadinessProvider(ResourceLocation id) {
        this.id = id;
    }

    public Optional<ManaProfileGeneration> getGeneration(EntityPlayer player) {
        return ManaService.getReadinessGeneration(player, id, ManaProfileGeneration::new);
    }

    /** Submit the first complete group. Pass ManaContribution.EMPTY for a verified empty group. */
    public ManaContributionPublicationResult completeInitial(
            EntityPlayer player, ManaProfileGeneration generation, ManaContribution contribution) {
        return ManaService.publishReadiness(player, id, generation, contribution, true, 0);
    }

    /**
     * Suspend this provider's recorded group before first acceptance. The provider stays required
     * and the generation keeps its original deadline and held mana. A successful receipt supplies
     * the revision for a verified {@link #update}; repeating suspension with that revision returns
     * the same revision. An accepted generation returns {@code ALREADY_ACCEPTED} without a change.
     */
    public ManaContributionPublicationResult suspend(EntityPlayer player, ManaProfileGeneration generation,
            long expectedRevision) {
        return ManaService.suspendReadiness(player, id, generation, expectedRevision);
    }

    /** Replace or recover the complete group using this provider's latest receipt revision. */
    public ManaContributionPublicationResult update(EntityPlayer player, ManaProfileGeneration generation,
            long expectedRevision, ManaContribution contribution) {
        return ManaService.publishReadiness(player, id, generation, contribution, false, expectedRevision);
    }
}
