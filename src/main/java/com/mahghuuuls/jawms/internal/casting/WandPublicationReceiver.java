package com.mahghuuuls.jawms.internal.casting;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Network-local exact-packet associations; no Minecraft state is read on the network thread. */
public final class WandPublicationReceiver<P> {
    public enum Delivery { APPLY, DISCARD, FAULT }
    private final Map<P, WandPublicationRecord> associated = new IdentityHashMap<>();
    private UUID epoch;
    private long latest;
    private Pending waiting;
    private boolean faulted;

    public synchronized void capability(UUID value) {
        if (epoch == null) epoch = java.util.Objects.requireNonNull(value, "epoch");
    }

    public synchronized boolean record(WandPublicationRecord record) {
        if (waiting != null) {
            // Any intervening marker breaks exact-next adjacency, even after an earlier fault.
            // Do not search historical markers for a later equal-shaped vanilla packet.
            waiting = null; fault();
        }
        boolean discard = epoch == null || !epoch.equals(record.epoch) || record.serial <= latest;
        if (!discard) latest = record.serial;
        if (record.kind == WandPublicationRecord.RETIRE) return !faulted && !discard;
        if (associated.size() >= WandPublicationRecord.MAX_PENDING) fault();
        // A fault revokes provenance, not fresh vanilla inventory delivery. Keep the serial watermark.
        waiting = new Pending(record, discard);
        return !faulted;
    }

    public synchronized Delivery packet(P packet, BiPredicate<WandPublicationRecord, P> shape) {
        if (waiting == null) return Delivery.APPLY;
        Pending pending = waiting;
        waiting = null;
        WandPublicationRecord record = pending.record;
        if (!shape.test(record, packet)) { fault(); return Delivery.FAULT; }
        if (pending.discard) return Delivery.DISCARD;
        if (faulted) return Delivery.APPLY;
        if (associated.containsKey(packet) || associated.size() >= WandPublicationRecord.MAX_PENDING) {
            fault(); return Delivery.FAULT;
        }
        associated.put(packet, record);
        return Delivery.APPLY;
    }

    /** Consume only at actual main-thread application, not PacketThreadUtil's scheduling exception. */
    public synchronized WandPublicationRecord take(P packet) { return associated.remove(packet); }
    public synchronized int pending() { return associated.size() + (waiting == null ? 0 : 1); }
    public synchronized boolean faulted() { return faulted; }
    public synchronized void fault() {
        if (faulted) return; // Later fault notifications must not lose a known stale pair's discard-only marker.
        faulted = true; waiting = null; associated.clear();
    }
    public synchronized void clear() { associated.clear(); waiting = null; epoch = null; latest = 0; faulted = false; }
    private static final class Pending {
        final WandPublicationRecord record;
        final boolean discard;
        Pending(WandPublicationRecord record, boolean discard) { this.record = record; this.discard = discard; }
    }
}
