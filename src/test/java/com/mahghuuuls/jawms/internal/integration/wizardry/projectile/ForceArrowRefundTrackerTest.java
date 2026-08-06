package com.mahghuuuls.jawms.internal.integration.wizardry.projectile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceArrowRefundTrackerTest {

    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void cleanTracker() {
        ForceArrowRefundTracker.clearPlayer(playerId);
    }

    @Test
    void successfulPromotionRefundsActualManaOnceOnBlock() {
        UUID projectileId = UUID.randomUUID();
        ForceArrowRefundTracker.capturePending(
                projectileId, playerId,
                PerfectionistPolicy.create(2L, true, 100.0D)
        );
        ForceArrowRefundTracker.promote(projectileId, playerId, 7, true);

        ForceArrowRefundTracker.Resolution first = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.BLOCK, true
        );
        ForceArrowRefundTracker.Resolution duplicate = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.EXPIRY, true
        );

        assertTrue(first.shouldRefund());
        assertEquals(7, first.getPaidMana());
        assertEquals(7, first.getRequestedRefund());
        assertEquals(playerId, first.getOwnerId());
        assertNull(duplicate);
        assertEquals(0, ForceArrowRefundTracker.size());
    }

    @Test
    void livingHitConsumesEligibilityWithoutRefund() {
        UUID projectileId = UUID.randomUUID();
        ForceArrowRefundTracker.capturePending(
                projectileId, playerId,
                PerfectionistPolicy.create(1L, true, 100.0D)
        );
        ForceArrowRefundTracker.promote(projectileId, playerId, 7, true);

        ForceArrowRefundTracker.Resolution result = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.LIVING_HIT, false
        );

        assertFalse(result.shouldRefund());
        assertEquals(0, ForceArrowRefundTracker.size());
    }

    @Test
    void failedCastAndFailedLaunchDiscardPendingState() {
        UUID failedCast = UUID.randomUUID();
        UUID failedLaunch = UUID.randomUUID();
        PerfectionistPolicy policy = PerfectionistPolicy.create(1L, true, 100.0D);
        ForceArrowRefundTracker.capturePending(failedCast, playerId, policy);
        ForceArrowRefundTracker.discardPending(
                failedCast, ForceArrowRefundTracker.Terminal.FAILED_CAST
        );
        ForceArrowRefundTracker.capturePending(failedLaunch, playerId, policy);
        ForceArrowRefundTracker.promote(failedLaunch, playerId, 7, false);

        assertEquals(0, ForceArrowRefundTracker.size());
    }

    @Test
    void inactiveOrZeroPaidLaunchNeverBecomesEligible() {
        UUID inactive = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        ForceArrowRefundTracker.capturePending(
                inactive, playerId,
                PerfectionistPolicy.create(1L, false, 100.0D)
        );
        ForceArrowRefundTracker.promote(inactive, playerId, 7, true);
        ForceArrowRefundTracker.capturePending(
                free, playerId,
                PerfectionistPolicy.create(1L, true, 100.0D)
        );
        ForceArrowRefundTracker.promote(free, playerId, 0, true);

        assertEquals(0, ForceArrowRefundTracker.size());
    }

    @Test
    void ownerMismatchCannotPromoteAnotherPlayersProjectile() {
        UUID projectileId = UUID.randomUUID();
        ForceArrowRefundTracker.capturePending(
                projectileId, playerId,
                PerfectionistPolicy.create(1L, true, 100.0D)
        );

        ForceArrowRefundTracker.promote(
                projectileId, UUID.randomUUID(), 7, true
        );

        assertEquals(1, ForceArrowRefundTracker.size());
        ForceArrowRefundTracker.promote(projectileId, playerId, 7, true);
        assertTrue(ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.EXPIRY, true
        ).shouldRefund());
    }

    @Test
    void promotionKeepsTheCapturedPercentageAndActualManaPortion() {
        UUID projectileId = UUID.randomUUID();
        PerfectionistPolicy launchSnapshot =
                PerfectionistPolicy.create(12L, true, 50.0D);
        ForceArrowRefundTracker.capturePending(projectileId, playerId, launchSnapshot);

        // Seven is the actual player-mana portion; any hunger-paid portion is absent here.
        ForceArrowRefundTracker.promote(projectileId, playerId, 7, true);
        ForceArrowRefundTracker.Resolution result = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.EXPIRY, true
        );

        assertTrue(result.shouldRefund());
        assertEquals(7, result.getPaidMana());
        assertEquals(3, result.getRequestedRefund());
    }

    @Test
    void playerAndStaleLifecycleCleanupRemoveTransientEntries() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PerfectionistPolicy policy = PerfectionistPolicy.create(1L, true, 100.0D);
        ForceArrowRefundTracker.capturePending(first, playerId, policy);
        ForceArrowRefundTracker.capturePending(second, playerId, policy);

        ForceArrowRefundTracker.clearPlayer(playerId);
        assertEquals(0, ForceArrowRefundTracker.size());

        ForceArrowRefundTracker.capturePending(first, playerId, policy);
        ForceArrowRefundTracker.clearStale(null);
        assertEquals(0, ForceArrowRefundTracker.size());
    }

    @Test
    void remoteTerminalCannotConsumeIntegratedServerEntry() {
        UUID projectileId = UUID.randomUUID();
        ForceArrowRefundTracker.capturePending(
                projectileId, playerId,
                PerfectionistPolicy.create(1L, true, 100.0D)
        );
        ForceArrowRefundTracker.promote(projectileId, playerId, 15, true);

        ForceArrowRefundTracker.Resolution remote = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.EXPIRY, true, true
        );

        assertNull(remote);
        assertEquals(1, ForceArrowRefundTracker.size());

        ForceArrowRefundTracker.Resolution server = ForceArrowRefundTracker.resolve(
                projectileId, ForceArrowRefundTracker.Terminal.EXPIRY, true, false
        );
        assertTrue(server.shouldRefund());
        assertEquals(15, server.getRequestedRefund());
        assertEquals(0, ForceArrowRefundTracker.size());
    }
}
