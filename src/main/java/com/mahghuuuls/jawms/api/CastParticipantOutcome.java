package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;
import java.util.Objects;

/** Immutable per-provider result. Unknown amount is NaN, never an invented zero. */
public final class CastParticipantOutcome {
    public enum Status { NOT_APPLICABLE, REFUSED, RELEASED, PAID, FREE, INVALIDATED, FAULT }
    public enum Failure { NONE, PREPARATION, HANDLE_REUSED, VALIDATION, SETTLEMENT, CLEANUP, NOTIFICATION }
    private final ResourceLocation participantId;
    private final Status status;
    private final Failure failure;
    private final double amount;
    private final boolean amountKnown;
    private final boolean stopAfterSuccess;
    private final String refusalCategory;

    public CastParticipantOutcome(ResourceLocation participantId, Status status, Failure failure,
            boolean amountKnown, double amount, boolean stopAfterSuccess, String refusalCategory) {
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        this.status = Objects.requireNonNull(status, "status");
        this.failure = Objects.requireNonNull(failure, "failure");
        if (amountKnown && (!Double.isFinite(amount) || amount < 0.0D)) {
            throw new IllegalArgumentException("Known amount must be finite and non-negative.");
        }
        this.amountKnown = amountKnown;
        this.amount = amountKnown ? amount : Double.NaN;
        this.stopAfterSuccess = stopAfterSuccess;
        this.refusalCategory = Objects.requireNonNull(refusalCategory, "refusalCategory");
        if (!refusalCategory.isEmpty() && !refusalCategory.matches("[a-z0-9_.:-]{1,64}")) {
            throw new IllegalArgumentException("Invalid refusal category.");
        }
    }
    public ResourceLocation getParticipantId() { return participantId; }
    public Status getStatus() { return status; }
    public Failure getFailure() { return failure; }
    public boolean isAmountKnown() { return amountKnown; }
    public double getAmount() { return amount; }
    public boolean mustStopAfterSuccess() { return stopAfterSuccess; }
    public String getRefusalCategory() { return refusalCategory; }
}
