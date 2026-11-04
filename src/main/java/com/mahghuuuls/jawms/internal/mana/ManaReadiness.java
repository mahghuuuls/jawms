package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
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
        slots.keySet().retainAll(eligible);
        for (ResourceLocation id : eligible) {
            slots.computeIfAbsent(id, unused -> new Slot());
        }
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
        return phase == ManaProfileReadiness.PENDING && tick - startTick >= DEADLINE_TICKS;
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
        if (context == null || slot.context != context || slot.token != token) {
            return rejection(STALE_GENERATION);
        }
        if (initial && slot.contribution != null) return rejection(ALREADY_COMPLETED);
        if (!initial && slot.contribution == null) return rejection(INITIAL_REQUIRED);
        if (!initial && slot.revision != expected) return rejection(REVISION_MISMATCH);
        long revision = Math.incrementExact(slot.revision);
        slot.contribution = contribution;
        slot.revision = revision;
        return new ManaContributionPublicationResult(initial ? INITIAL_RECORDED : UPDATE_RECORDED,
                OptionalLong.of(revision));
    }

    static ManaContributionPublicationResult rejection(ManaContributionPublicationStatus status) {
        return new ManaContributionPublicationResult(status, OptionalLong.empty());
    }

    private static final class Slot {
        private Object context;
        private ManaProfileGeneration token;
        private ManaContribution contribution;
        private long revision;
    }
}
