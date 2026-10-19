package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import java.util.UUID;

/** Single-connection input/close state. It holds no player, world, payment or callback. */
public final class ClientUseState {
    public static final long CONFIRMATION_NANOS = 30_000_000_000L;
    private UUID epoch;
    private long sequence;
    private long releasedThrough;
    private long use;
    private EnumHand hand;
    private ResourceLocation spell;
    private ParticipantUseMessages.CloseCause close;
    private boolean pending;
    private boolean captured;
    private boolean bound;
    private boolean faulted;
    private boolean discardedGesture;
    private long remaining;

    public void capability(UUID value) {
        if (epoch != null) return; // Recovery is a real reconnect, not a new packet.
        epoch = value;
    }

    public long input(EnumHand candidateHand, ResourceLocation candidateSpell) {
        if (epoch == null) return 0;
        if (blocked()) { rejectInput(); return 0; }
        if (bound && sequence > releasedThrough && !matches(candidateHand, candidateSpell)) { rejectInput(); return 0; }
        if (sequence <= releasedThrough) {
            if (sequence == Long.MAX_VALUE) { faulted = true; return 0; }
            sequence++;
            use = 0;
            close = null;
            captured = false;
            bound = false;
        }
        hand = candidateHand;
        spell = candidateSpell;
        return sequence;
    }

    public long release() {
        discardedGesture = false;
        if (sequence <= releasedThrough) return 0;
        releasedThrough = sequence;
        // Vanilla/Forge can skip ItemWand's callback. A real bound release still owns the barrier.
        if (bound && !captured && !faulted) startPending();
        return sequence;
    }
    public void bind(EnumHand hand, ResourceLocation spell) {
        if (sequence > releasedThrough && matches(hand, spell)) bound = true;
    }
    public boolean bound() { return bound; }
    public boolean unreleased() { return sequence > releasedThrough; }

    public boolean capture(EnumHand actualHand, ResourceLocation actualSpell) {
        if (!matches(actualHand, actualSpell) || faulted || captured) return false;
        captured = true;
        startPending();
        return true;
    }

    private void startPending() {
        if (pending) return;
        pending = true;
        remaining = CONFIRMATION_NANOS;
    }

    public boolean accept(ParticipantUseMessages.Close message) {
        if (faulted || epoch == null || !epoch.equals(message.epoch())
                || sequence != message.intent() || !matches(message.hand(), message.spell())
                || (use != 0 && use != message.use()) || close != null) return false;
        use = message.use();
        close = message.cause();
        return true;
    }
    public boolean associateUse(java.util.UUID epoch, long intent, long use) {
        if (faulted || this.epoch == null || !this.epoch.equals(epoch) || this.sequence != intent
                || use <= 0 || (this.use != 0 && this.use != use)) return false;
        this.use = use;
        return true;
    }
    public void protocolFault() { faulted = true; pending = false; }
    /** Resolution removes pending before callbacks; a later gesture must still revoke the old callback owner. */
    public boolean ownsFinish(long intent, long use) {
        return !faulted && sequence == intent && (use == 0 || this.use == use);
    }

    /** Called with monotonic connected/unpaused elapsed time, never restarted by traffic. */
    public boolean elapse(long nanos) {
        if (!pending || faulted || nanos <= 0) return false;
        remaining = nanos >= remaining ? 0 : remaining - nanos;
        if (remaining != 0) return false;
        faulted = true;
        pending = false;
        return true;
    }

    public boolean ready() { return pending && close != null; }
    public boolean finishPermitted() { return ready() && close == ParticipantUseMessages.CloseCause.NORMAL_FINISH; }
    public void resolved() { pending = false; captured = true; }
    /** A blocked physical press is consumed, not queued for resolution of the old use. */
    public void rejectInput() { discardedGesture = true; }
    public boolean pending() { return pending; }
    public boolean faulted() { return faulted; }
    public boolean enabled() { return epoch != null; }
    public UUID epoch() { return epoch; }
    public long intent() { return sequence; }
    public long use() { return use; }
    public long remainingNanos() { return pending ? remaining : 0; }
    public boolean blocked() { return faulted || pending || discardedGesture || (close != null && sequence > releasedThrough); }
    public boolean suppresses(EnumHand h, ResourceLocation s) { return matches(h, s) && close != null; }
    public boolean matches(EnumHand h, ResourceLocation s) { return hand == h && spell != null && spell.equals(s); }
}
