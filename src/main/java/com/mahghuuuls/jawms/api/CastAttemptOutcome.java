package com.mahghuuuls.jawms.api;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Final coordination facts; no borrowed player, world, stack or provider is retained. */
public final class CastAttemptOutcome {
    public enum Status {
        LEGACY, ACCEPTED, REFUSED, INVALID_BEFORE_EFFECT, FALSE_EFFECT, EFFECT_EXCEPTION,
        INVALID_AFTER_EFFECT, SETTLEMENT_FAULT, CLEANUP_FAULT
    }
    private final UUID playerId;
    private final String useId, attemptId;
    private final ResourceLocation spellId;
    private final EnumHand hand;
    private final Status status;
    private final boolean effectInvoked, effectSuccessful, localAmountKnown;
    private final int paidMana, paidHunger;
    private final List<CastParticipantOutcome> participants;

    public CastAttemptOutcome(CastAttemptContext context, Status status, boolean effectInvoked,
            boolean effectSuccessful, boolean localAmountKnown, int paidMana, int paidHunger,
            List<CastParticipantOutcome> participants) {
        this.playerId = context.getPlayer().getUniqueID();
        this.useId = context.getUseId();
        this.attemptId = context.getAttemptId();
        this.spellId = context.getSpellId();
        this.hand = context.getHand();
        this.status = Objects.requireNonNull(status, "status");
        if (paidMana < 0 || paidHunger < 0) throw new IllegalArgumentException("Actual payment");
        this.effectInvoked = effectInvoked;
        this.effectSuccessful = effectSuccessful;
        this.localAmountKnown = localAmountKnown;
        this.paidMana = paidMana;
        this.paidHunger = paidHunger;
        this.participants = Collections.unmodifiableList(new ArrayList<>(participants));
    }
    public UUID getPlayerId() { return playerId; }
    public String getUseId() { return useId; }
    public String getAttemptId() { return attemptId; }
    public ResourceLocation getSpellId() { return spellId; }
    public EnumHand getHand() { return hand; }
    public Status getStatus() { return status; }
    public boolean wasEffectInvoked() { return effectInvoked; }
    public boolean wasEffectSuccessful() { return effectSuccessful; }
    public boolean isLocalAmountKnown() { return localAmountKnown; }
    public int getPaidMana() { return paidMana; }
    public int getPaidHunger() { return paidHunger; }
    public List<CastParticipantOutcome> getParticipants() { return participants; }
    public boolean isAccepted() { return status == Status.ACCEPTED; }
    public boolean hasSettledPayment() {
        if (paidMana > 0 || paidHunger > 0) return true;
        for (CastParticipantOutcome participant : participants) {
            if (participant.getStatus() == CastParticipantOutcome.Status.PAID) return true;
        }
        return false;
    }
    public boolean mustStopAfterSuccess() {
        for (CastParticipantOutcome participant : participants) {
            if (participant.mustStopAfterSuccess()) return true;
        }
        return false;
    }
}
