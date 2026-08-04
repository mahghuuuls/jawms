package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastPaymentPlanTest {

    private static final DemonicSealPolicy SEAL =
            new DemonicSealPolicy(true, 4L, 5, 6, false);

    @Test
    void allocatesManaFirstAndRoundsOnlyTheShortfallUp() {
        CastPaymentPlan plan = CastPaymentPlan.calculate(
                12, 7, 20, 0, SEAL, false
        );

        assertTrue(plan.isAffordable());
        assertTrue(plan.wasSealConsidered());
        assertEquals(7, plan.getMana());
        assertEquals(1, plan.getHunger());
        assertEquals(14, plan.getUsableHunger());
    }

    @Test
    void preservesFloorAndAccountsForNestedHungerClaims() {
        CastPaymentPlan exactFloor = CastPaymentPlan.calculate(
                10, 0, 8, 0, SEAL, false
        );
        CastPaymentPlan overFloor = CastPaymentPlan.calculate(
                11, 0, 8, 0, SEAL, false
        );
        CastPaymentPlan nested = CastPaymentPlan.calculate(
                5, 0, 8, 2, SEAL, false
        );

        assertTrue(exactFloor.isAffordable());
        assertEquals(2, exactFloor.getHunger());
        assertFalse(overFloor.isAffordable());
        assertFalse(nested.isAffordable());
        assertEquals(0, nested.getUsableHunger());
    }

    @Test
    void continuousSubstitutionRequiresItsCapturedOption() {
        CastPaymentPlan disabled = CastPaymentPlan.calculate(
                5, 0, 20, 0, SEAL, true
        );
        DemonicSealPolicy enabledPolicy =
                new DemonicSealPolicy(true, 5L, 5, 6, true);
        CastPaymentPlan enabled = CastPaymentPlan.calculate(
                5, 0, 20, 0, enabledPolicy, true
        );

        assertFalse(disabled.isAffordable());
        assertFalse(disabled.wasSealConsidered());
        assertTrue(enabled.isAffordable());
        assertEquals(1, enabled.getHunger());
    }

    @Test
    void inactiveSealRetainsManaOnlyBehavior() {
        CastPaymentPlan affordable = CastPaymentPlan.calculate(
                7, 7, 20, 0, DemonicSealPolicy.inactive(), false
        );
        CastPaymentPlan rejected = CastPaymentPlan.calculate(
                8, 7, 20, 0, DemonicSealPolicy.inactive(), false
        );

        assertTrue(affordable.isAffordable());
        assertEquals(0, affordable.getHunger());
        assertFalse(rejected.isAffordable());
        assertFalse(rejected.wasSealConsidered());
    }

    @Test
    void capturedPolicyRetainsItsOwnRulesRevisionAndValues() {
        WizardryIntegrationRules first = WizardryIntegrationRules.defaults(21L);
        DemonicSealPolicy captured = DemonicSealPolicy.capture(first, true);
        WizardryIntegrationRules later = WizardryIntegrationRules.defaults(22L);

        assertTrue(captured.isActive());
        assertEquals(21L, captured.getRulesRevision());
        assertEquals(first.getDemonicSealManaPerHunger(), captured.getManaPerHunger());
        assertEquals(first.getDemonicSealMinimumHunger(), captured.getMinimumHunger());
        assertEquals(22L, later.getRevision());
        assertFalse(DemonicSealPolicy.capture(first, false).isActive());
    }
}
