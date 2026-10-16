package com.mahghuuuls.jawms.api;

import java.util.Objects;

/** Explicit applicability/refusal/authorization. Never use null as a result. */
public final class CastCostPreparation {
    public enum Status { NOT_APPLICABLE, REFUSED, ACCEPTED }

    private final Status status;
    private final ICastCostReservation reservation;
    private final String category;

    private CastCostPreparation(Status status, ICastCostReservation reservation, String category) {
        this.status = status;
        this.reservation = reservation;
        this.category = category;
    }

    public static CastCostPreparation notApplicable() {
        return new CastCostPreparation(Status.NOT_APPLICABLE, null, "");
    }

    public static CastCostPreparation refused(String category) {
        Objects.requireNonNull(category, "category");
        if (!category.matches("[a-z0-9_.:-]{1,64}")) {
            throw new IllegalArgumentException("Refusal category must be 1-64 lowercase identifier characters.");
        }
        return new CastCostPreparation(Status.REFUSED, null, category);
    }

    public static CastCostPreparation accepted(ICastCostReservation reservation) {
        return new CastCostPreparation(Status.ACCEPTED,
                Objects.requireNonNull(reservation, "reservation"), "");
    }

    public Status getStatus() { return status; }
    public ICastCostReservation getReservation() { return reservation; }
    public String getCategory() { return category; }
}
