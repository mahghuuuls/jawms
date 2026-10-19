package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import net.minecraft.util.EnumHand;
import java.util.UUID;

/** Event-loop-owned scalar gate for delivery of an already emitted close; never a permission/source producer. */
final class PublicationReadiness {
    enum Boundary { NONE, SLOT, WINDOW, DIRECT, RETIREMENT, CONTEXT, BLOCKED_ONLY }
    private final Boundary boundary;
    private final boolean requireBlockedPress;
    private UUID epoch;
    private long intent;
    private EnumHand hand;
    private int inventorySlot;
    private ParticipantUseMessages.Close close;
    private WandPublicationRecord.Entry reached;
    private boolean blocked, context;

    PublicationReadiness(Boundary boundary, boolean requireBlockedPress) {
        this.boundary = boundary; this.requireBlockedPress = requireBlockedPress;
    }
    void pending(UUID epoch, long intent, EnumHand hand, int inventorySlot) {
        this.epoch = epoch; this.intent = intent; this.hand = hand; this.inventorySlot = inventorySlot;
        reached = null; blocked = context = false;
        if (close != null && !owns(close)) close = null;
    }
    void close(ParticipantUseMessages.Close value) {
        if (value.isValid() && (epoch == null || owns(value))) close = value;
    }
    void publication(WandPublicationRecord record) {
        if (epoch == null || !epoch.equals(record.epoch)) return;
        boolean shape = boundary == Boundary.SLOT && record.kind == WandPublicationRecord.SLOT && record.window == 0
                || boundary == Boundary.WINDOW && record.kind == WandPublicationRecord.WINDOW && record.window == 0
                || boundary == Boundary.DIRECT && record.kind == WandPublicationRecord.SLOT && record.window == -2
                || boundary == Boundary.RETIREMENT && record.kind == WandPublicationRecord.RETIRE;
        if (!shape) return;
        int mappedSlot = boundary == Boundary.RETIREMENT ? -1 : boundary == Boundary.DIRECT ? inventorySlot
                : inventorySlot == 40 ? 45 : 36 + inventorySlot;
        for (WandPublicationRecord.Entry entry : record.entries())
            if (entry.intent == intent && entry.inventorySlot == inventorySlot && entry.windowSlot == mappedSlot
                    && (close == null || entry.use == close.use() && entry.generation == close.generation())) reached = entry;
    }
    void blocked(UUID epoch, long intent) { if (owns(epoch, intent)) blocked = true; }
    void context(UUID epoch, long intent) { if (owns(epoch, intent)) context = true; }
    boolean ready() {
        return close != null && owns(close) && (!requireBlockedPress || blocked)
                && (boundary == Boundary.BLOCKED_ONLY || (boundary == Boundary.CONTEXT ? context : reached != null
                && reached.use == close.use() && reached.generation == close.generation()));
    }
    private boolean owns(ParticipantUseMessages.Close value) {
        return owns(value.epoch(), value.intent()) && value.hand() == hand;
    }
    private boolean owns(UUID epoch, long intent) { return this.epoch != null && this.epoch.equals(epoch) && this.intent == intent; }
}
