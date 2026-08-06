package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardrySorceryAttributionTest {

    @Test
    void acceptsOnlyCreditedForceDamageOrOwnedForceOrb() {
        assertEquals(WizardrySorceryAttribution.Category.FORCE_DAMAGE,
                classify(true, true, false, false));
        assertEquals(WizardrySorceryAttribution.Category.FORCE_ORB,
                classify(true, false, true, true));
        assertTrue(WizardrySorceryAttribution.isSorcery(
                WizardrySorceryAttribution.Category.FORCE_DAMAGE
        ));
        assertTrue(WizardrySorceryAttribution.isSorcery(
                WizardrySorceryAttribution.Category.FORCE_ORB
        ));
    }

    @Test
    void wrongPlayerAndAmbiguousIndirectEvidenceFailClosed() {
        assertEquals(WizardrySorceryAttribution.Category.WRONG_PLAYER,
                classify(false, true, true, true));
        assertEquals(WizardrySorceryAttribution.Category.AMBIGUOUS,
                classify(true, false, true, false));
        assertEquals(WizardrySorceryAttribution.Category.AMBIGUOUS,
                classify(true, false, false, false));
        assertFalse(WizardrySorceryAttribution.isSorcery(
                WizardrySorceryAttribution.Category.AMBIGUOUS
        ));
    }

    private static WizardrySorceryAttribution.Category classify(
            boolean credited, boolean force, boolean orb, boolean orbOwned
    ) {
        return WizardrySorceryAttribution.classifyEvidence(
                credited, force, orb, orbOwned
        );
    }
}
