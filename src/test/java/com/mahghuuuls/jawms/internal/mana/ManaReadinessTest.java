package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;


class ManaReadinessTest {
    private static final ResourceLocation BONUS = new ResourceLocation("test", "bonus");
    private static final ResourceLocation PEER = new ResourceLocation("test", "peer");
    private static final ManaContribution PLUS_TWENTY = ManaContribution.builder().flatMaximumMana(20).build();

    @Test
    void completeEmptyIsDistinctFromMissingAndUpdatesCompareWithLatestPublication() {
        ManaReadiness readiness = new ManaReadiness(new LinkedHashSet<>(Arrays.asList(BONUS, PEER)), 20, false);
        Object world = new Object();
        ManaProfileGeneration bonus = issue(readiness, BONUS, world);
        ManaProfileGeneration peer = issue(readiness, PEER, world);

        assertEquals(STALE_GENERATION, readiness.publish(BONUS, world, peer, PLUS_TWENTY, true, 0).getStatus());
        assertEquals(INITIAL_REQUIRED, readiness.publish(BONUS, world, bonus, PLUS_TWENTY, false, 1).getStatus());
        assertEquals(1, readiness.publish(BONUS, world, bonus, PLUS_TWENTY, true, 0).getRevision().getAsLong());
        assertEquals(Collections.singletonList(PEER), readiness.missing());
        assertEquals(ALREADY_COMPLETED, readiness.publish(BONUS, world, bonus, ManaContribution.EMPTY, true, 0).getStatus());
        assertSame(PLUS_TWENTY, readiness.contribution(BONUS));
        assertEquals(1, readiness.publish(PEER, world, peer, ManaContribution.EMPTY, true, 0).getRevision().getAsLong());
        assertTrue(readiness.missing().isEmpty());
        assertEquals(2, readiness.publish(BONUS, world, bonus, ManaContribution.EMPTY, false, 1).getRevision().getAsLong());
        assertEquals(REVISION_MISMATCH, readiness.publish(BONUS, world, bonus, PLUS_TWENTY, false, 1).getStatus());
        assertTrue(readiness.contribution(BONUS).isEmpty());
    }

    @Test
    void suspendedSlotRetainsItsReceiptAndRequiresARevisionedCompleteUpdate() {
        ManaReadiness readiness = new ManaReadiness(new LinkedHashSet<>(Arrays.asList(BONUS, PEER)), 20, false);
        Object world = new Object();
        ManaProfileGeneration bonus = issue(readiness, BONUS, world);
        ManaProfileGeneration peer = issue(readiness, PEER, world);

        assertEquals(INITIAL_REQUIRED, readiness.suspend(BONUS, world, bonus, 1).getStatus());
        assertEquals(STALE_GENERATION, readiness.suspend(BONUS, world, peer, 1).getStatus());
        assertEquals(INAPPLICABLE, readiness.suspend(new ResourceLocation("test", "absent"), world, bonus, 1)
                .getStatus());
        readiness.publish(BONUS, world, bonus, ManaContribution.EMPTY, true, 0);
        readiness.publish(PEER, world, peer, PLUS_TWENTY, true, 0);
        readiness.unavailable();

        assertEquals(2, readiness.suspend(BONUS, world, bonus, 1).getRevision().getAsLong());
        assertEquals(Collections.singletonList(BONUS), readiness.missing());
        assertSame(PLUS_TWENTY, readiness.contribution(PEER));
        assertEquals(ManaProfileReadiness.UNAVAILABLE, readiness.phase());
        long suspendedVersion = readiness.publicationVersion();
        assertEquals(2, readiness.suspend(BONUS, world, bonus, 2).getRevision().getAsLong());
        assertEquals(suspendedVersion, readiness.publicationVersion());
        assertEquals(REVISION_MISMATCH, readiness.suspend(BONUS, world, bonus, 1).getStatus());
        assertEquals(ALREADY_COMPLETED, readiness.publish(BONUS, world, bonus, PLUS_TWENTY, true, 0).getStatus());
        assertEquals(REVISION_MISMATCH, readiness.publish(BONUS, world, bonus, PLUS_TWENTY, false, 1).getStatus());
        assertEquals(3, readiness.publish(BONUS, world, bonus, ManaContribution.EMPTY, false, 2)
                .getRevision().getAsLong());
        assertTrue(readiness.missing().isEmpty());
    }

    @Test
    void deadlineIsOneShotAndPolicyRemovalRevokesTheTokenWithoutRestartingTheClock() {
        ManaReadiness readiness = new ManaReadiness(Collections.singleton(BONUS), 20, false);
        Object world = new Object();
        ManaProfileGeneration original = issue(readiness, BONUS, world);
        assertFalse(readiness.deadlineElapsed(119));
        readiness.applyPolicy(Collections.emptySet());
        assertEquals(INAPPLICABLE, readiness.publish(BONUS, world, original, PLUS_TWENTY, true, 0).getStatus());
        readiness.applyPolicy(Collections.singleton(BONUS));
        ManaProfileGeneration replacement = issue(readiness, BONUS, world);
        assertNotSame(original, replacement);
        assertTrue(readiness.deadlineElapsed(120));
        readiness.unavailable();
        assertFalse(readiness.deadlineElapsed(121));
        assertEquals(STALE_GENERATION, readiness.publish(BONUS, world, original, PLUS_TWENTY, true, 0).getStatus());
        assertEquals(INITIAL_RECORDED, readiness.publish(BONUS, world, replacement, PLUS_TWENTY, true, 0).getStatus());
        assertEquals(ManaProfileReadiness.UNAVAILABLE, readiness.phase());
        readiness.accept();
        assertEquals(ManaProfileReadiness.READY, readiness.phase());
        readiness.retire();
        assertEquals(STALE_GENERATION, readiness.publish(BONUS, world, replacement, PLUS_TWENTY, false, 1).getStatus());
        assertFalse(readiness.generation(BONUS, world, ManaReadinessTest::newToken).isPresent());
    }

    private static ManaProfileGeneration issue(ManaReadiness readiness, ResourceLocation id, Object world) {
        return readiness.generation(id, world, ManaReadinessTest::newToken).get();
    }

    static ManaProfileGeneration newToken() {
        try {
            java.lang.reflect.Constructor<ManaProfileGeneration> constructor =
                    ManaProfileGeneration.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
