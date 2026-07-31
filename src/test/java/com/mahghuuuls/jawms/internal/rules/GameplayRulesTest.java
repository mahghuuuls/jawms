package com.mahghuuuls.jawms.internal.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameplayRulesTest {

    @Test
    void approvedDefaultsAreAvailableBeforeConfigurationLoads() {
        GameplayRules rules = GameplayRules.initialDefaults();

        assertEquals(50, rules.getBaseMaximum());
        assertEquals(2.0D, rules.getBaseRegeneration());
        assertEquals(40L, rules.getRegenerationLockoutTicks());
        assertEquals("min(original_cost, tier * 25)", rules.getCostFormula());
        assertEquals(20, rules.getSmallFlask());
        assertEquals(50, rules.getMediumFlask());
        assertEquals(100, rules.getLargeFlask());
        assertEquals(15, rules.getArmorProfile().getHelmetMaximum());
        assertEquals(15, rules.getArmorProfile().getChestMaximum());
        assertEquals(10, rules.getArmorProfile().getLegsMaximum());
        assertEquals(10, rules.getArmorProfile().getBootsMaximum());
    }

    @Test
    void lockoutConversionRoundsUpAndSaturates() {
        assertEquals(0L, GameplayRules.secondsToTicks(0.0D));
        assertEquals(1L, GameplayRules.secondsToTicks(0.001D));
        assertEquals(21L, GameplayRules.secondsToTicks(1.0001D));
        assertEquals(Long.MAX_VALUE, GameplayRules.secondsToTicks(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> GameplayRules.secondsToTicks(-1.0D));
        assertThrows(IllegalArgumentException.class, () -> GameplayRules.secondsToTicks(Double.NaN));
    }

}
