package com.mahghuuuls.jawms.internal.integration.wizardry;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.modifier.ManaProfileResolver;
import com.mahghuuuls.jawms.internal.modifier.ResolvedManaProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardryPassiveContributionAdapterTest {

    @Test
    void heldAggregationSupportsHighestAndSumWithoutNegativeLevels() {
        assertEquals(3, WizardryPassiveContributionAdapter.aggregateLevels(
                1, 3, WizardryIntegrationRules.HeldWandAggregation.HIGHEST
        ));
        assertEquals(4, WizardryPassiveContributionAdapter.aggregateLevels(
                1, 3, WizardryIntegrationRules.HeldWandAggregation.SUM
        ));
        assertEquals(2, WizardryPassiveContributionAdapter.aggregateLevels(
                -4, 2, WizardryIntegrationRules.HeldWandAggregation.SUM
        ));
        assertEquals(Integer.MAX_VALUE, WizardryPassiveContributionAdapter.aggregateLevels(
                Integer.MAX_VALUE, 3, WizardryIntegrationRules.HeldWandAggregation.SUM
        ));
    }

    @Test
    void storageAndCondenserUseApprovedPerLevelDefaults() {
        WizardryIntegrationRules rules = WizardryIntegrationRules.defaults(1L);

        ManaContribution contribution = WizardryPassiveContributionAdapter.heldContribution(
                3, 3, rules
        );

        assertEquals(30.0D, contribution.getMaximumManaIncrease());
        assertEquals(30.0D, contribution.getRegenerationIncrease());
        assertNull(WizardryPassiveContributionAdapter.heldContribution(0, 0, rules));
    }

    @Test
    void percentageWandUpgradesScaleTheBaseAndArmorFlatBonuses() {
        ManaContribution upgrades = WizardryPassiveContributionAdapter.heldContribution(
                3, 3, WizardryIntegrationRules.defaults(1L)
        );

        ResolvedManaProfile withoutArmor = ManaProfileResolver.resolve(
                60, 2.4D, 2.0D, Collections.singletonList(upgrades)
        );
        assertEquals(78, withoutArmor.getMaximumMana());
        assertEquals(3.12D, withoutArmor.getRegeneration(), 0.0000001D);

        ManaContribution fullArmor = ManaContribution.builder()
                .flatMaximumMana(40)
                .flatRegeneration(1.6D)
                .build();
        ResolvedManaProfile withArmor = ManaProfileResolver.resolve(
                60, 2.4D, 2.0D, Arrays.asList(fullArmor, upgrades)
        );
        assertEquals(130, withArmor.getMaximumMana());
        assertEquals(5.2D, withArmor.getRegeneration(), 0.0000001D);
    }

    @Test
    void installedTotalsUseTheSameSaturationExposedToPresentation() {
        WizardryIntegrationRules rules = new WizardryIntegrationRules(
                1L,
                true, Double.MAX_VALUE, true, Double.MAX_VALUE,
                true, 3, WizardryIntegrationRules.HeldWandAggregation.SUM,
                true, 2.0D, 4, true, 0.5D, true, 30.0D,
                true, 5, true, 100.0D, true, 5, 6, false,
                true, 1.0D, true, false
        );

        assertEquals(Double.MAX_VALUE,
                WizardryPassiveContributionAdapter.storageMaximumIncrease(2, rules));
        assertEquals(Double.MAX_VALUE,
                WizardryPassiveContributionAdapter.condenserRegenerationIncrease(2, rules));
    }

    @Test
    void condensingStacksPerActiveRingAndFontUsesIndependentGates() {
        WizardryIntegrationRules rules = WizardryIntegrationRules.defaults(2L);
        ManaContribution oneCondensing = WizardryPassiveContributionAdapter
                .condensingContribution(1, rules);
        ManaContribution twoCondensing = WizardryPassiveContributionAdapter
                .condensingContribution(2, rules);
        ManaContribution fontTwo = WizardryPassiveContributionAdapter
                .fontContribution(1, rules);

        ResolvedManaProfile withOneRing = ManaProfileResolver.resolve(
                60, 2.4D, 2.0D, Arrays.asList(oneCondensing, fontTwo)
        );
        ResolvedManaProfile withTwoRings = ManaProfileResolver.resolve(
                60, 2.4D, 2.0D, Arrays.asList(twoCondensing, fontTwo)
        );

        assertEquals(4.9D, withOneRing.getRegeneration(), 0.0000001D);
        assertEquals(5.4D, withTwoRings.getRegeneration(), 0.0000001D);
        assertTrue(withTwoRings.canRegenerateDuringPostCastLockout());
        assertFalse(withTwoRings.canRegenerateDuringContinuousCasting());
    }

    @Test
    void passiveContributionCombinesWithDistinctGeneralContribution() {
        ManaContribution storage = WizardryPassiveContributionAdapter.heldContribution(
                1, 0, WizardryIntegrationRules.defaults(1L)
        );
        ManaContribution configured = ManaContribution.builder()
                .flatMaximumMana(5)
                .maximumManaIncrease(50)
                .build();

        ResolvedManaProfile profile = ManaProfileResolver.resolve(
                50, 2.0D, 2.0D, Arrays.asList(storage, configured)
        );

        assertEquals(88, profile.getMaximumMana());
        assertEquals(2.0D, ManaProfileResolver.resolve(
                50, 2.0D, 2.0D, Collections.emptyList()
        ).getRegeneration());
    }
}
