package com.mahghuuuls.jawms.internal.integration.wizardry.projectile;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable launch-time Perfectionist policy. */
public final class PerfectionistPolicy {

    private final long rulesRevision;
    private final boolean active;
    private final double refundPercent;

    private PerfectionistPolicy(long rulesRevision, boolean active, double refundPercent) {
        this.rulesRevision = rulesRevision;
        this.active = active;
        this.refundPercent = refundPercent;
    }

    public static PerfectionistPolicy capture(
            WizardryIntegrationRules rules, boolean artefactActive
    ) {
        return new PerfectionistPolicy(
                rules.getRevision(),
                rules.isPerfectionistEnabled() && artefactActive,
                rules.getPerfectionistRefund()
        );
    }

    public static PerfectionistPolicy inactive(long rulesRevision) {
        return new PerfectionistPolicy(rulesRevision, false, 0.0D);
    }

    static PerfectionistPolicy create(
            long rulesRevision, boolean active, double refundPercent
    ) {
        return new PerfectionistPolicy(rulesRevision, active, refundPercent);
    }

    public int refundFor(int paidMana) {
        if (!active || paidMana <= 0 || refundPercent <= 0.0D) {
            return 0;
        }
        BigDecimal refund = BigDecimal.valueOf(paidMana)
                .multiply(BigDecimal.valueOf(refundPercent))
                .movePointLeft(2)
                .setScale(0, RoundingMode.FLOOR);
        if (refund.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, refund.intValue());
    }

    public long getRulesRevision() { return rulesRevision; }
    public boolean isActive() { return active; }
    public double getRefundPercent() { return refundPercent; }
}
