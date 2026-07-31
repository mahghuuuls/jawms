package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaProfileResolverTest {

    @Test
    void resolvesSharedArithmeticAndIndependentReductions() {
        ManaContribution values = ManaContribution.builder()
                .flatMaximumMana(10)
                .maximumManaIncrease(20.0D)
                .maximumManaReduction(25.0D)
                .flatRegeneration(2.0D)
                .regenerationIncrease(50.0D)
                .regenerationReduction(50.0D)
                .flatLockoutSeconds(1.0D)
                .lockoutIncrease(50.0D)
                .lockoutReduction(50.0D)
                .build();

        ResolvedManaProfile profile = ManaProfileResolver.resolve(
                50,
                2.0D,
                2.0D,
                Collections.singletonList(values)
        );

        assertEquals(54, profile.getMaximumMana());
        assertEquals(3.0D, profile.getRegeneration());
        assertEquals(2.25D, profile.getLockoutSeconds());
        assertEquals(45L, profile.getLockoutTicks());

        ManaContribution half = ManaContribution.builder()
                .maximumManaReduction(50.0D)
                .build();
        assertEquals(12, ManaProfileResolver.resolve(
                50,
                2.0D,
                2.0D,
                Arrays.asList(half, half)
        ).getMaximumMana());
    }

    @Test
    void clampsSubtotalsFloorsOnceAndSaturates() {
        ManaContribution penalty = ManaContribution.builder()
                .flatMaximumMana(Integer.MIN_VALUE)
                .flatRegeneration(-Double.MAX_VALUE)
                .flatLockoutSeconds(-Double.MAX_VALUE)
                .build();
        ResolvedManaProfile minimum = ManaProfileResolver.resolve(
                50,
                2.0D,
                2.0D,
                Collections.singletonList(penalty)
        );
        assertEquals(1, minimum.getMaximumMana());
        assertEquals(0.0D, minimum.getRegeneration());
        assertEquals(0L, minimum.getLockoutTicks());

        ManaContribution huge = ManaContribution.builder()
                .maximumManaIncrease(Double.MAX_VALUE)
                .regenerationIncrease(Double.MAX_VALUE)
                .lockoutIncrease(Double.MAX_VALUE)
                .build();
        ResolvedManaProfile maximum = ManaProfileResolver.resolve(
                Integer.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Collections.singletonList(huge)
        );
        assertEquals(Integer.MAX_VALUE, maximum.getMaximumMana());
        assertEquals(Double.MAX_VALUE, maximum.getRegeneration());
        assertEquals(Long.MAX_VALUE, maximum.getLockoutTicks());
    }

    @Test
    void combinesEfficiencyAndGatePermissions() {
        ManaContribution first = ManaContribution.builder()
                .globalSpellEfficiency(10.0D)
                .elementSpellEfficiency(Element.FIRE, 20.0D)
                .regenerateDuringPostCastLockout(true)
                .build();
        ManaContribution second = ManaContribution.builder()
                .globalSpellEfficiency(5.0D)
                .elementSpellEfficiency(Element.FIRE, 7.0D)
                .regenerateDuringContinuousCasting(true)
                .build();

        ResolvedManaProfile profile = ManaProfileResolver.resolve(
                50,
                2.0D,
                2.0D,
                Arrays.asList(first, second)
        );

        assertEquals(42.0D, profile.getSpellEfficiency(Element.FIRE));
        assertEquals(15.0D, profile.getSpellEfficiency(Element.ICE));
        assertTrue(profile.canRegenerateDuringPostCastLockout());
        assertTrue(profile.canRegenerateDuringContinuousCasting());
        assertFalse(ManaProfileResolver.resolve(
                50,
                2.0D,
                2.0D,
                Collections.emptyList()
        ).canRegenerateDuringPostCastLockout());
    }
}
