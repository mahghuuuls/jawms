package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Connection-local correlation, never resource authorization. Accessed on the server thread. */
final class ParticipantUseState {
    private final UUID epoch;
    private long newestIntent;
    private long releasedThrough;
    private EnumHand intentHand;
    private ResourceLocation intentSpell;
    private Use current;

    ParticipantUseState(UUID epoch) {
        this.epoch = Objects.requireNonNull(epoch, "epoch");
    }

    boolean intent(UUID suppliedEpoch, long sequence, EnumHand hand, ResourceLocation spell) {
        if (!epoch.equals(suppliedEpoch) || sequence <= 0 || hand == null || spell == null
                || sequence < newestIntent || sequence <= releasedThrough) return false;
        if (sequence == newestIntent) {
            if (current != null) return hand == current.hand && spell.equals(current.spell);
            intentHand = hand;
            intentSpell = spell;
            return true;
        }
        if (current != null && releasedThrough < current.intent) return false;
        newestIntent = sequence;
        intentHand = hand;
        intentSpell = spell;
        current = null;
        return true;
    }

    Use bind(EnumHand hand, ResourceLocation spell, Object owningStack, long newUseId) {
        if (newestIntent <= releasedThrough || hand != intentHand || !Objects.equals(spell, intentSpell)
                || owningStack == null || newUseId <= 0) return null;
        if (current == null) current = new Use(newestIntent, newUseId, hand, spell, owningStack);
        return current.ended || current.stack != owningStack ? null : current;
    }

    boolean release(UUID suppliedEpoch, long sequence) {
        if (!epoch.equals(suppliedEpoch) || sequence <= 0 || sequence > newestIntent
                || sequence <= releasedThrough) return false;
        releasedThrough = sequence;
        if (current != null && current.intent <= releasedThrough) current.blocked = false;
        return true;
    }

    boolean owns(Use use) {
        return use != null && current == use && use.intent == newestIntent;
    }

    boolean stop(Use use) {
        return close(use, ParticipantUseMessages.CloseCause.TERMINAL);
    }

    boolean close(Use use, ParticipantUseMessages.CloseCause cause) {
        if (!owns(use) || use.ended) return false;
        use.suppressFinish = true;
        use.ended = true;
        use.stack = null;
        use.cause = Objects.requireNonNull(cause, "cause");
        use.blocked = releasedThrough < use.intent;
        return true;
    }

    Use current() { return current; }
    long newestIntent() { return newestIntent; }
    boolean isOpen(Use use) { return owns(use) && !use.ended; }

    boolean matchesOpen(Use use, EnumHand hand, ResourceLocation spell, Object stack) {
        return isOpen(use) && use.hand == hand && use.spell.equals(spell) && use.stack == stack;
    }

    boolean retryBlocked() {
        return current != null && current.blocked;
    }

    boolean suppressesFinish(EnumHand hand, ResourceLocation spell) {
        return current != null && current.suppressFinish && current.hand == hand
                && current.spell.equals(spell);
    }

    UUID epoch() { return epoch; }

    static final class Use {
        final long intent;
        final long id;
        final EnumHand hand;
        final ResourceLocation spell;
        Object stack;
        long generation;
        private boolean ended;
        private boolean blocked;
        private boolean suppressFinish;
        ParticipantUseMessages.CloseCause cause;
        CastingDiagnostics.UseSummary diagnostics;

        private Use(long intent, long id, EnumHand hand, ResourceLocation spell, Object stack) {
            this.intent = intent;
            this.id = id;
            this.hand = hand;
            this.spell = spell;
            this.stack = stack;
        }
    }
}
