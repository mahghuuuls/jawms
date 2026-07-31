package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientSpellCostPresentationTest {

    @Test
    void formatsWholeAndDecimalBaseCostsWithoutNoise() {
        assertEquals("0", ClientSpellCostPresentation.formatBaseCost(0.0D));
        assertEquals("25", ClientSpellCostPresentation.formatBaseCost(25.0D));
        assertEquals("12.5", ClientSpellCostPresentation.formatBaseCost(12.5D));
        assertEquals("0.125", ClientSpellCostPresentation.formatBaseCost(0.125D));
    }

    @Test
    void rejectsValuesThatCannotBeValidSynchronizedCosts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientSpellCostPresentation.formatBaseCost(-1.0D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientSpellCostPresentation.formatBaseCost(Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientSpellCostPresentation.formatBaseCost(
                        Double.POSITIVE_INFINITY
                )
        );
    }
}
