package com.mahghuuuls.jawms.internal.integration.wizardry.projectile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfectionistPolicyTest {

    @Test
    void defaultPercentageRefundsAllActualPaidMana() {
        PerfectionistPolicy policy = PerfectionistPolicy.create(4L, true, 100.0D);

        assertEquals(7, policy.refundFor(7));
        assertEquals(4L, policy.getRulesRevision());
    }

    @Test
    void partialPercentageRoundsDownAndNeverRefundsUnpaidMana() {
        PerfectionistPolicy policy = PerfectionistPolicy.create(1L, true, 50.0D);

        assertEquals(3, policy.refundFor(7));
        assertEquals(0, policy.refundFor(0));
    }

    @Test
    void inactiveAndZeroPercentagePoliciesRefundNothing() {
        assertEquals(0, PerfectionistPolicy.create(1L, false, 100.0D).refundFor(7));
        assertEquals(0, PerfectionistPolicy.create(1L, true, 0.0D).refundFor(7));
    }
}
