package com.mahghuuuls.jawms.internal.casting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousSpellCostTest {

    @Test
    void oddPerSecondCostAlternatesCeilingThenFloorEveryHalfSecond() {
        assertEquals(3, ContinuousSpellCost.portionAtTick(5, 0));
        assertEquals(0, ContinuousSpellCost.portionAtTick(5, 1));
        assertEquals(2, ContinuousSpellCost.portionAtTick(5, 10));
        assertEquals(3, ContinuousSpellCost.portionAtTick(5, 20));
        assertEquals(2, ContinuousSpellCost.portionAtTick(5, 30));
    }

    @Test
    void twoHalfSecondPortionsAlwaysEqualTheConfiguredPerSecondCost() {
        for (int cost = 0; cost < 100; cost++) {
            assertEquals(
                    cost,
                    ContinuousSpellCost.portionAtTick(cost, 0)
                            + ContinuousSpellCost.portionAtTick(cost, 10)
            );
        }
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ContinuousSpellCost.portionAtTick(-1, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ContinuousSpellCost.portionAtTick(1, -1)
        );
    }
}
