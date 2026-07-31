package com.mahghuuuls.jawms.api;

import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaContributionTest {

    @Test
    void builderProducesCompleteImmutableContribution() {
        ManaContribution contribution = ManaContribution.builder()
                .flatMaximumMana(10)
                .maximumManaIncrease(20.0D)
                .maximumManaReduction(25.0D)
                .flatRegeneration(-0.5D)
                .regenerationIncrease(50.0D)
                .regenerationReduction(10.0D)
                .flatLockoutSeconds(1.0D)
                .lockoutIncrease(30.0D)
                .lockoutReduction(40.0D)
                .globalSpellEfficiency(5.0D)
                .elementSpellEfficiency(Element.FIRE, 20.0D)
                .regenerateDuringContinuousCasting(true)
                .build();

        assertEquals(10, contribution.getFlatMaximumMana());
        assertEquals(20.0D, contribution.getMaximumManaIncrease());
        assertEquals(25.0D, contribution.getMaximumManaReduction());
        assertEquals(-0.5D, contribution.getFlatRegeneration());
        assertEquals(20.0D, contribution.getElementSpellEfficiency(Element.FIRE));
        assertEquals(0.0D, contribution.getElementSpellEfficiency(Element.ICE));
        assertTrue(contribution.canRegenerateDuringContinuousCasting());
        assertFalse(contribution.canRegenerateDuringPostCastLockout());
        assertFalse(contribution.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.getElementSpellEfficiency().put(Element.ICE, 1.0D));
    }

    @Test
    void invalidPercentagesAndDecimalsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().maximumManaIncrease(-1.0D).build());
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().regenerationReduction(100.1D).build());
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().flatLockoutSeconds(Double.NaN).build());
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().globalSpellEfficiency(-1.0D).build());
        assertTrue(ManaContribution.EMPTY.isEmpty());
    }
}
