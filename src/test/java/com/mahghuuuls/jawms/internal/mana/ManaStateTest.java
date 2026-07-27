package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaStateTest {

    @Test
    void newStateStartsFull() {
        ManaState state = new ManaState();

        assertEquals(1000, state.getCurrentMana());
        assertEquals(1000, state.getMaximumMana());
    }

    @Test
    void consumptionIsAtomicAndRestorationClamps() {
        ManaState state = new ManaState();

        assertTrue(state.tryConsume(980));
        assertFalse(state.tryConsume(30));
        assertEquals(20, state.getCurrentMana());
        assertEquals(980, state.restore(5000));
        assertEquals(1000, state.getCurrentMana());
    }

    @Test
    void negativeMutationsAreRejected() {
        ManaState state = new ManaState();

        assertThrows(IllegalArgumentException.class, () -> state.restore(-1));
        assertThrows(IllegalArgumentException.class, () -> state.tryConsume(-1));
        assertEquals(1000, state.getCurrentMana());
    }

    @Test
    void fractionalRegenerationAccumulatesWithoutExposingFractions() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        for (int tick = 0; tick < 39; tick++) {
            state.regenerateTick(0.5D);
        }
        assertEquals(998, state.getCurrentMana());

        state.regenerateTick(0.5D);
        assertEquals(999, state.getCurrentMana());
    }

    @Test
    void defaultRegenerationRestoresOneManaAfterTwentyTicks() {
        ManaState state = new ManaState();
        state.tryConsume(2);

        for (int tick = 0; tick < 19; tick++) {
            assertEquals(0, state.regenerateTick(1.0D));
        }
        assertEquals(1, state.regenerateTick(1.0D));
        assertEquals(999, state.getCurrentMana());
    }

    @Test
    void persistenceStoresOnlyBoundedCurrentMana() {
        ManaState original = new ManaState();
        original.tryConsume(400);
        original.regenerateTick(0.5D);

        NBTTagCompound saved = original.serializeIntrinsicState();
        assertEquals(1, saved.getKeySet().size());
        assertTrue(saved.hasKey("currentMana"));

        ManaState restored = new ManaState();
        restored.deserializeIntrinsicState(saved);
        assertEquals(600, restored.getCurrentMana());

        saved.setInteger("currentMana", 5000);
        restored.deserializeIntrinsicState(saved);
        assertEquals(1000, restored.getCurrentMana());
    }

    @Test
    void cloneCopiesOnlyBoundedCurrentManaAndAdvancesRevision() {
        ManaState original = new ManaState();
        original.tryConsume(250);
        ManaState clone = new ManaState();

        clone.copyFrom(original);

        assertEquals(750, clone.getCurrentMana());
        assertTrue(clone.getRevision() > original.getRevision());
    }

    @Test
    void invalidRegenerationRatesAreRejected() {
        ManaState state = new ManaState();

        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(-0.1D));
        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.regenerateTick(Double.POSITIVE_INFINITY));
    }
}
