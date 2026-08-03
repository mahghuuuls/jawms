package com.mahghuuuls.jawms.internal.integration.wizardry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlaskAllocationTest {

    @Test
    void playerReceivesTheWholeBudgetBeforeArmor() {
        FlaskAllocation.Plan plan = FlaskAllocation.plan(
                20, 30,
                Collections.singletonList(new FlaskAllocation.ArmorState(0, 100))
        );
        assertEquals(20, plan.playerAmount);
        assertEquals(0, plan.armorAmount);
        assertTrue(plan.hasDestination());
    }

    @Test
    void onlyTheRemainderGoesToTheLeastFullArmorPiece() {
        FlaskAllocation.Plan plan = FlaskAllocation.plan(
                20, 5,
                Arrays.asList(
                        new FlaskAllocation.ArmorState(80, 100),
                        new FlaskAllocation.ArmorState(10, 100),
                        new FlaskAllocation.ArmorState(40, 50)
                )
        );
        assertEquals(5, plan.playerAmount);
        assertEquals(1, plan.armorIndex);
        assertEquals(15, plan.armorAmount);
        assertEquals(20, plan.playerAmount + plan.armorAmount);
    }

    @Test
    void armorCapacityBoundsTheSingleSharedBudget() {
        FlaskAllocation.Plan plan = FlaskAllocation.plan(
                100, 10,
                Arrays.asList(
                        new FlaskAllocation.ArmorState(98, 100),
                        new FlaskAllocation.ArmorState(90, 100)
                )
        );
        assertEquals(10, plan.playerAmount);
        assertEquals(1, plan.armorIndex);
        assertEquals(10, plan.armorAmount);
        assertEquals(20, plan.playerAmount + plan.armorAmount);
    }

    @Test
    void fullDestinationsAndZeroBudgetCannotStart() {
        FlaskAllocation.Plan full = FlaskAllocation.plan(
                20, 0,
                Collections.singletonList(new FlaskAllocation.ArmorState(100, 100))
        );
        FlaskAllocation.Plan zero = FlaskAllocation.plan(
                0, 50,
                Collections.singletonList(new FlaskAllocation.ArmorState(0, 100))
        );
        assertFalse(full.hasDestination());
        assertFalse(zero.hasDestination());
    }
}
