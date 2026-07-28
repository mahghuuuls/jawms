package com.mahghuuuls.jawms.internal.casting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinuousFinishPolicyTest {

    @Test
    void successfulServerCastFinishesAfterOrdinaryInterruption() {
        assertEquals(
                Integer.MAX_VALUE,
                ContinuousFinishPolicy.affordability(false, true, false)
        );
    }

    @Test
    void manaExhaustionAndFailedStartSuppressServerFinish() {
        assertEquals(
                -1,
                ContinuousFinishPolicy.affordability(false, true, true)
        );
        assertEquals(
                -1,
                ContinuousFinishPolicy.affordability(false, false, false)
        );
    }

    @Test
    void clientPreservesOrdinaryFinishButSuppressesExhaustion() {
        assertEquals(
                Integer.MAX_VALUE,
                ContinuousFinishPolicy.affordability(false, true, false)
        );
        assertEquals(
                -1,
                ContinuousFinishPolicy.affordability(false, true, true)
        );
    }

    @Test
    void creativePathPreservesNormalFinishBehavior() {
        assertEquals(
                Integer.MAX_VALUE,
                ContinuousFinishPolicy.affordability(true, false, true)
        );
    }
}
