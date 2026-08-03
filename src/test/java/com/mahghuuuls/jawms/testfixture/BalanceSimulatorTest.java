package com.mahghuuuls.jawms.testfixture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceSimulatorTest {

    @Test
    void recorderUsesTheFirstPayableContinuousPortionForStartup() {
        assertEquals(6, BalanceRecorder.requiredCostForAttempt(11, true));
        assertEquals(5, BalanceRecorder.requiredCostForAttempt(10, true));
        assertEquals(11, BalanceRecorder.requiredCostForAttempt(11, false));
    }

    @Test
    void frozenRotationIdentifiersAndInputsAreStable() {
        List<BalanceRotation> rotations = new ArrayList<>(BalanceRotation.all());
        assertEquals(3, rotations.size());
        Set<String> ids = new HashSet<>();
        for (BalanceRotation rotation : rotations) {
            assertTrue(ids.add(rotation.getId()));
            assertFalse(rotation.getSteps().isEmpty());
            assertTrue(rotation.getCadenceTicks() > 0);
            assertTrue(rotation.getDurationTicks() > rotation.getCadenceTicks());
        }

        BalanceRotation representative = BalanceRotation.find("representative_fire");
        assertNotNull(representative);
        assertEquals(100, representative.getExpectedMaximum());
        assertEquals(4.0D, representative.getExpectedRegeneration());
        assertEquals(40L, representative.getExpectedLockoutTicks());
        assertEquals(40.0D, representative.getExpectedEfficiency());
        assertEquals(75, representative.getCadenceTicks());
        assertEquals(40, representative.getCueCount());
        assertEquals(5, representative.getSteps().size());
        assertEquals("novice", representative.getSteps().get(0).getTier());
        assertEquals("master", representative.getSteps().get(3).getTier());
        assertTrue(representative.getSteps().get(3).isContinuous());
        assertEquals(20, representative.getSteps().get(3).getContinuousHoldTicks());
    }

    @Test
    void lowRotationIsSustainableForTheFullWindow() {
        BalanceSimulator.Result result = BalanceSimulator.simulate(
                BalanceRotation.find("low_novice")
        );

        assertEquals(-1, result.getFirstUnaffordableTick());
        assertEquals(1200, result.getElapsedTicks());
        assertEquals(12, result.getSuccessfulActions());
        assertEquals(50, result.getEndingMana());
        assertEquals(60, result.getSpentMana());
        assertEquals(60, result.getRegeneratedMana());
    }

    @Test
    void representativeRotationPredictsFirstFlaskInsideTargetWindow() {
        BalanceSimulator.Result result = BalanceSimulator.simulate(
                BalanceRotation.find("representative_fire")
        );

        assertEquals(2400, result.getFirstUnaffordableTick());
        assertEquals(32, result.getSuccessfulActions());
        assertEquals(9, result.getEndingMana());
        assertEquals(288, result.getSpentMana());
        assertEquals(197, result.getRegeneratedMana());
        assertTrue(result.getElapsedTicks() >= 1800);
        assertTrue(result.getElapsedTicks() <= 3000);
    }

    @Test
    void aggressiveRotationAccountsForContinuousPortionsAndDepletesFaster() {
        BalanceSimulator.Result aggressive = BalanceSimulator.simulate(
                BalanceRotation.find("aggressive_fire")
        );
        BalanceSimulator.Result representative = BalanceSimulator.simulate(
                BalanceRotation.find("representative_fire")
        );

        assertEquals(520, aggressive.getFirstUnaffordableTick());
        assertEquals(8, aggressive.getSuccessfulActions());
        assertEquals(0, aggressive.getEndingMana());
        assertEquals(136, aggressive.getSpentMana());
        assertEquals(36, aggressive.getRegeneratedMana());
        assertTrue(aggressive.getElapsedTicks() < representative.getElapsedTicks());
    }
}
