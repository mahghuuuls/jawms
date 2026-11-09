package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;

/** Bounded publication slots and acceptance phase for one player lifecycle. */
final class ManaReadiness {
    enum Lifecycle {
        LOGIN, RESPAWN, END_RETURN, DIMENSION_TRANSFER, TRANSFER_RECOVERY
    }

    private static final int DEADLINE_TICKS = 100;

    final UUID identity = UUID.randomUUID();
    private final Map<ResourceLocation, Slot> slots = new LinkedHashMap<>();
    private final int startTick;
    private long publicationVersion;
    private ManaProfileReadiness phase;
    private boolean retired;
    Lifecycle lifecycle = Lifecycle.LOGIN;
    WorldServer world;
    boolean bound;
    boolean complete;

    ManaReadiness(Set<ResourceLocation> eligible, int tick, boolean hasAcceptedProfile) {
        startTick = tick;
        phase = hasAcceptedProfile ? ManaProfileReadiness.READY : ManaProfileReadiness.PENDING;
        applyPolicy(eligible);
    }

    void applyPolicy(Set<ResourceLocation> eligible) {
        if (retired) return;
        boolean changed = slots.keySet().retainAll(eligible);
        for (ResourceLocation id : eligible) {
            if (!slots.containsKey(id)) {
                slots.put(id, new Slot());
                changed = true;
            }
        }
        if (changed) publicationVersion++;
    }

    long publicationVersion() {
        return publicationVersion;
    }

    ManaProfileReadiness phase() {
        return phase;
    }

    boolean held() {
        return phase != ManaProfileReadiness.READY;
    }

    List<ResourceLocation> missing() {
        List<ResourceLocation> missing = new ArrayList<>();
        slots.forEach((id, slot) -> {
            if (slot.contribution == null) missing.add(id);
        });
        return missing;
    }

    boolean deadlineElapsed(int tick) {
        return phase == ManaProfileReadiness.PENDING && elapsedTicks(tick) >= DEADLINE_TICKS;
    }

    int elapsedTicks(int tick) {
        return tick - startTick;
    }

    void unavailable() {
        phase = ManaProfileReadiness.UNAVAILABLE;
    }

    void accept() {
        phase = ManaProfileReadiness.READY;
    }

    void retire() {
        unavailable();
        retired = true;
        slots.clear();
    }

    boolean isRetired() {
        return retired;
    }

    boolean hasEligibleProvider() {
        return !slots.isEmpty();
    }

    ManaContribution contribution(ResourceLocation id) {
        Slot slot = slots.get(id);
        return slot == null ? null : slot.contribution;
    }

    Set<ResourceLocation> eligibleProviders() {
        return Collections.unmodifiableSet(slots.keySet());
    }

    /** Read under the publication fence of a complete candidate. */
    Publication recordedPublication(ResourceLocation id) {
        Slot slot = slots.get(id);
        return new Publication(slot.token, slot.revision, slot.contribution);
    }

    Optional<ManaProfileGeneration> generation(ResourceLocation id, Object context,
            Supplier<ManaProfileGeneration> create) {
        Slot slot = slots.get(id);
        if (slot == null || context == null) return Optional.empty();
        if (slot.context == null) {
            slot.context = context;
            slot.token = create.get();
        }
        return slot.context == context ? Optional.of(slot.token) : Optional.empty();
    }

    ManaContributionPublicationResult publish(ResourceLocation id, Object context,
            ManaProfileGeneration token, ManaContribution contribution, boolean initial, long expected) {
        if (retired) return rejection(STALE_GENERATION);
        Slot slot = slots.get(id);
        if (slot == null) return rejection(INAPPLICABLE);
        if (!slot.matches(context, token)) {
            return rejection(STALE_GENERATION);
        }
        if (initial && slot.revision != 0) return rejection(ALREADY_COMPLETED);
        if (!initial && slot.revision == 0) return rejection(INITIAL_REQUIRED);
        if (!initial && slot.revision != expected) return rejection(REVISION_MISMATCH);
        long revision = Math.incrementExact(slot.revision);
        slot.contribution = contribution;
        slot.revision = revision;
        publicationVersion++;
        return receipt(initial ? INITIAL_RECORDED : UPDATE_RECORDED, revision);
    }

    boolean matchesPublication(ResourceLocation id, Object context, ManaProfileGeneration token) {
        Slot slot = slots.get(id);
        return !retired && slot != null && slot.matches(context, token);
    }

    ManaContributionPublicationResult suspend(ResourceLocation id, Object context,
            ManaProfileGeneration token, long expected) {
        if (retired || context == null) return rejection(STALE_GENERATION);
        Slot slot = slots.get(id);
        if (slot == null) return rejection(INAPPLICABLE);
        if (!slot.matches(context, token)) return rejection(STALE_GENERATION);
        if (!held()) return rejection(ALREADY_ACCEPTED);
        if (slot.revision == 0) return rejection(INITIAL_REQUIRED);
        if (slot.revision != expected) return rejection(REVISION_MISMATCH);
        if (slot.contribution != null) {
            slot.revision = Math.incrementExact(slot.revision);
            slot.contribution = null;
            publicationVersion++;
        }
        return receipt(SUSPENDED, slot.revision);
    }

    private static ManaContributionPublicationResult receipt(ManaContributionPublicationStatus status, long revision) {
        return new ManaContributionPublicationResult(status, OptionalLong.of(revision));
    }

    static ManaContributionPublicationResult rejection(ManaContributionPublicationStatus status) {
        return new ManaContributionPublicationResult(status, OptionalLong.empty());
    }

    static final class Publication {
        final ManaProfileGeneration token;
        final long revision;
        final ManaContribution contribution;

        private Publication(ManaProfileGeneration token, long revision, ManaContribution contribution) {
            this.token = token;
            this.revision = revision;
            this.contribution = contribution;
        }
    }

    private static final class Slot {
        private Object context;
        private ManaProfileGeneration token;
        private ManaContribution contribution;
        // Zero means no initial publication. A positive revision with no group means suspended.
        private long revision;

        private boolean matches(Object publicationContext, ManaProfileGeneration generation) {
            return publicationContext != null && context == publicationContext && token == generation;
        }
    }
}
