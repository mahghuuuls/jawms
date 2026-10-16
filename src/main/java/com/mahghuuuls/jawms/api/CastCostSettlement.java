package com.mahghuuuls.jawms.api;

/** Actual provider receipt. Amounts use the registering provider's documented units. */
public final class CastCostSettlement {
    public enum Status { PAID, FREE, INVALIDATED }
    private final Status status;
    private final double amount;
    private final boolean stopAfterSuccess;

    private CastCostSettlement(Status status, double amount, boolean stopAfterSuccess) {
        this.status = status;
        this.amount = amount;
        this.stopAfterSuccess = stopAfterSuccess;
    }

    public static CastCostSettlement paid(double amount, boolean stopAfterSuccess) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Paid amount must be finite and positive; use free for exemption.");
        }
        return new CastCostSettlement(Status.PAID, amount, stopAfterSuccess);
    }

    public static CastCostSettlement free(boolean stopAfterSuccess) {
        return new CastCostSettlement(Status.FREE, 0.0D, stopAfterSuccess);
    }

    /** No paid amount is asserted for an invalidated claim. */
    public static CastCostSettlement invalidated() {
        return new CastCostSettlement(Status.INVALIDATED, Double.NaN, false);
    }

    public Status getStatus() { return status; }
    public boolean isAmountKnown() { return status != Status.INVALIDATED; }
    public double getAmount() { return amount; }
    public boolean mustStopAfterSuccess() { return stopAfterSuccess; }
}
