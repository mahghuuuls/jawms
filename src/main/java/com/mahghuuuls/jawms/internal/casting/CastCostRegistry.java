package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.api.ICastCostParticipant;
import net.minecraft.util.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Startup-only composition; ordering is lexicographic on the complete namespaced ID. */
public final class CastCostRegistry {
    private static final CastCostRegistry INSTANCE = new CastCostRegistry();
    private final Map<ResourceLocation, Entry> entries = new TreeMap<>(
            Comparator.comparing(ResourceLocation::toString));
    private boolean frozen;

    CastCostRegistry() { }
    public static CastCostRegistry instance() { return INSTANCE; }

    public synchronized void register(ResourceLocation id, ICastCostParticipant participant) {
        Objects.requireNonNull(id, "participantId");
        Objects.requireNonNull(participant, "participant");
        if (!id.toString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid namespaced cast-cost participant ID.");
        }
        if (frozen) throw new IllegalStateException("Cast-cost registration is frozen.");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate participant: " + id);
        entries.put(id, new Entry(id, participant));
    }

    public synchronized void freeze() { frozen = true; }
    public synchronized boolean isEmpty() { return entries.isEmpty(); }
    synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }
    static final class Entry {
        final ResourceLocation id;
        final ICastCostParticipant participant;
        Entry(ResourceLocation id, ICastCostParticipant participant) {
            this.id = id;
            this.participant = participant;
        }
    }
}
