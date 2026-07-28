package com.mahghuuuls.jawms.client;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientContinuousCastGuardTest {

    @AfterEach
    void reset() {
        ClientContinuousCastGuard.clear();
    }

    @Test
    void rejectionBlocksOnlyTheMatchingHandAndSpellUntilCleared() {
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "flame_ray");
        ClientContinuousCastGuard.block(EnumHand.OFF_HAND, spellId, false);

        assertTrue(ClientContinuousCastGuard.isBlocked(EnumHand.OFF_HAND, spellId));
        assertFalse(ClientContinuousCastGuard.isBlocked(EnumHand.MAIN_HAND, spellId));
        assertFalse(ClientContinuousCastGuard.isBlocked(
                EnumHand.OFF_HAND,
                new ResourceLocation("ebwizardry", "frost_ray")
        ));

        ClientContinuousCastGuard.clear();
        assertFalse(ClientContinuousCastGuard.isBlocked(EnumHand.OFF_HAND, spellId));
    }

    @Test
    void immediateResetSuppressesOnlyTheMatchingSpellFinishUntilCleared() {
        ResourceLocation spellId = new ResourceLocation("ebwizardry", "flame_ray");
        ClientContinuousCastGuard.block(EnumHand.MAIN_HAND, spellId, true);

        assertTrue(ClientContinuousCastGuard.isFinishSuppressed(
                EnumHand.MAIN_HAND,
                spellId
        ));
        assertFalse(ClientContinuousCastGuard.isFinishSuppressed(
                EnumHand.OFF_HAND,
                spellId
        ));
        assertFalse(ClientContinuousCastGuard.isFinishSuppressed(
                EnumHand.MAIN_HAND,
                new ResourceLocation("ebwizardry", "grapple")
        ));

        ClientContinuousCastGuard.clear();
        assertFalse(ClientContinuousCastGuard.isFinishSuppressed(
                EnumHand.MAIN_HAND,
                spellId
        ));
    }
}
