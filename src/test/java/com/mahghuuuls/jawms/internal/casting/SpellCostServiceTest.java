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
    void zeroBaseCostRemainsFreeAndInvalidContextualFactorsAreNeutral() {
        assertEquals(0, SpellCostService.roundFinalCost(0.0D, Float.POSITIVE_INFINITY));
        assertEquals(10, SpellCostService.roundFinalCost(10.0D, 0.0F));
        assertEquals(10, SpellCostService.roundFinalCost(10.0D, -1.0F));
        assertEquals(10, SpellCostService.roundFinalCost(10.0D, Float.NaN));
    }

    @Test
    void unrepresentablePositiveContextualCostSaturates() {
        assertEquals(
                Integer.MAX_VALUE,
                SpellCostService.roundFinalCost(Integer.MAX_VALUE, 2.0F)
        );
        assertEquals(10, SpellCostService.roundFinalCost(10.0D, Float.POSITIVE_INFINITY));
    }

    @Test
    void efficiencyUsesApprovedDiminishingReturnsCurve() {
        assertEquals(100, SpellCostService.roundFinalCost(100, 1, 0, 100));
        assertEquals(84, SpellCostService.roundFinalCost(100, 1, 20, 100));
        assertEquals(72, SpellCostService.roundFinalCost(100, 1, 40, 100));
        assertEquals(56, SpellCostService.roundFinalCost(100, 1, 80, 100));
        assertEquals(50, SpellCostService.roundFinalCost(100, 1, 100, 100));
        assertEquals(34, SpellCostService.roundFinalCost(100, 1, 200, 100));
        assertEquals(1, SpellCostService.roundFinalCost(0.01D, 1, Double.MAX_VALUE, 100));
    }

    @Test
    void profileMultiplierComposesWithContextAndEfficiencyBeforeOneFinalCeiling() {
        assertEquals(38, SpellCostService.roundFinalCost(100, 1.0F, 0.75D, 100, 100));
        assertEquals(75, SpellCostService.roundFinalCost(100, 0.5F, 1.5D, 0, 100));
        assertEquals(1, SpellCostService.roundFinalCost(1, 1.0F, 0.5D, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> SpellCostService.roundFinalCost(10, 1.0F, 0.0D, 0, 100));
    }

    @Test
    void finiteExtremeFactorsDoNotOverflowBeforeCancellation() {
        assertEquals(50, SpellCostService.roundFinalCost(
                100, 1.0F, 1.0D, Double.MAX_VALUE, Double.MAX_VALUE
        ));
        assertEquals(2, SpellCostService.roundFinalCost(
                2, 1.0F, Double.MAX_VALUE, Double.MAX_VALUE, 1.0D
        ));
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
