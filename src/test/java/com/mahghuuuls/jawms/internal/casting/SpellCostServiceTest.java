package com.mahghuuuls.jawms.internal.casting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpellCostServiceTest {

    @Test
    void contextualPositiveCostRoundsUp() {
        assertEquals(8, SpellCostService.roundFinalCost(10.0D, 0.75F));
        assertEquals(1, SpellCostService.roundFinalCost(0.01D, 1.0F));
        assertEquals(15, SpellCostService.roundFinalCost(10.0D, 1.5F));
    }

    @Test
    void zeroBaseCostRemainsFreeAndNonPositiveMultiplierCannotCreateCost() {
        assertEquals(0, SpellCostService.roundFinalCost(0.0D, Float.POSITIVE_INFINITY));
        assertEquals(0, SpellCostService.roundFinalCost(10.0D, 0.0F));
        assertEquals(0, SpellCostService.roundFinalCost(10.0D, -1.0F));
    }

    @Test
    void unrepresentablePositiveContextualCostSaturates() {
        assertEquals(
                Integer.MAX_VALUE,
                SpellCostService.roundFinalCost(Integer.MAX_VALUE, 2.0F)
        );
        assertEquals(
                Integer.MAX_VALUE,
                SpellCostService.roundFinalCost(10.0D, Float.NaN)
        );
        assertEquals(
                Integer.MAX_VALUE,
                SpellCostService.roundFinalCost(10.0D, Float.POSITIVE_INFINITY)
        );
    }

    @Test
    void invalidCompiledBaseCostIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellCostService.roundFinalCost(-1.0D, 1.0F)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellCostService.roundFinalCost(Double.NaN, 1.0F)
        );
    }
}
