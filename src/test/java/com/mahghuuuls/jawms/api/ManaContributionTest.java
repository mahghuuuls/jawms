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
                .globalSpellCostMultiplier(0.5D)
                .elementSpellCostMultiplier(Element.FIRE, 1.5D)
                .regenerateDuringContinuousCasting(true)
                .build();

        assertEquals(10, contribution.getFlatMaximumMana());
        assertEquals(20.0D, contribution.getMaximumManaIncrease());
        assertEquals(25.0D, contribution.getMaximumManaReduction());
        assertEquals(-0.5D, contribution.getFlatRegeneration());
        assertEquals(20.0D, contribution.getElementSpellEfficiency(Element.FIRE));
        assertEquals(0.0D, contribution.getElementSpellEfficiency(Element.ICE));
        assertEquals(0.5D, contribution.getGlobalSpellCostMultiplier());
        assertEquals(1.5D, contribution.getElementSpellCostMultiplier(Element.FIRE));
        assertEquals(1.0D, contribution.getElementSpellCostMultiplier(Element.ICE));
        assertTrue(contribution.canRegenerateDuringContinuousCasting());
        assertFalse(contribution.canRegenerateDuringPostCastLockout());
        assertFalse(contribution.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.getElementSpellEfficiency().put(Element.ICE, 1.0D));
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.getElementSpellCostMultipliers().put(Element.ICE, 1.0D));
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
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().globalSpellCostMultiplier(0.0D).build());
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder()
                        .elementSpellCostMultiplier(Element.FIRE, Double.NaN).build());
        assertTrue(ManaContribution.EMPTY.isEmpty());
    }

    @Test
    void directionalFlatLockoutHelpersUseOneSignedLastCallWinsField() {
        assertEquals(2.5D, ManaContribution.builder()
                .flatLockoutReductionSeconds(1.0D)
                .flatLockoutIncreaseSeconds(2.5D)
                .build().getFlatLockoutSeconds());
        assertEquals(-1.5D, ManaContribution.builder()
                .flatLockoutSeconds(4.0D)
                .flatLockoutReductionSeconds(1.5D)
                .build().getFlatLockoutSeconds());
        assertEquals(-2.0D, ManaContribution.builder()
                .flatLockoutIncreaseSeconds(4.0D)
                .flatLockoutReductionSeconds(2.0D)
                .build().getFlatLockoutSeconds());
        assertEquals(3.0D, ManaContribution.builder()
                .flatLockoutReductionSeconds(1.0D)
                .flatLockoutSeconds(3.0D)
                .build().getFlatLockoutSeconds());
        assertEquals(2.0D, ManaContribution.builder()
                .flatLockoutSeconds(-4.0D)
                .flatLockoutIncreaseSeconds(2.0D)
                .build().getFlatLockoutSeconds());
        assertEquals(-3.0D, ManaContribution.builder()
                .flatLockoutIncreaseSeconds(1.0D)
                .flatLockoutSeconds(-3.0D)
                .build().getFlatLockoutSeconds());

        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().flatLockoutIncreaseSeconds(-0.1D));
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().flatLockoutReductionSeconds(-0.1D));
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().flatLockoutIncreaseSeconds(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ManaContribution.builder().flatLockoutReductionSeconds(Double.POSITIVE_INFINITY));
    }
}
