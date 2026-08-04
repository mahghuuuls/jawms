package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CastPaymentContextManagerTest {

    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        CastPaymentContextManager.clearClientSide();
    }

    @Test
    void captureIfAbsentPreservesPhysicalCastPolicyUntilExplicitClear() {
        WizardryIntegrationRules first = rules(2L, 5);
        WizardryIntegrationRules replacement = rules(3L, 4);

        DemonicSealPolicy captured = CastPaymentContextManager.capture(
                playerId, true, EnumHand.MAIN_HAND, first, true
        );
        DemonicSealPolicy retained = CastPaymentContextManager.captureIfAbsent(
                playerId, true, EnumHand.MAIN_HAND, replacement, true
        );

        assertSame(captured, retained);
        assertEquals(2L, retained.getRulesRevision());
        assertEquals(5, retained.getManaPerHunger());

        CastPaymentContextManager.clearClientSide();
        DemonicSealPolicy nextCast = CastPaymentContextManager.captureIfAbsent(
                playerId, true, EnumHand.MAIN_HAND, replacement, true
        );

        assertEquals(3L, nextCast.getRulesRevision());
        assertEquals(4, nextCast.getManaPerHunger());
    }

    private static WizardryIntegrationRules rules(long revision, int manaPerHunger) {
        return new WizardryIntegrationRules(
                revision,
                true, 10.0D,
                true, 10.0D,
                true, 3,
                WizardryIntegrationRules.HeldWandAggregation.HIGHEST,
                true, 2.0D, 4,
                true, 0.5D,
                true, 30.0D,
                true, 5,
                true, 100.0D,
                true, manaPerHunger, 6, false,
                true, 1.0D, true, false
        );
    }
}
