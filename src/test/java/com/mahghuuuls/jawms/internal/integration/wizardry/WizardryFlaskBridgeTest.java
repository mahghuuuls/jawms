package com.mahghuuuls.jawms.internal.integration.wizardry;

import electroblob.wizardry.item.ItemManaFlask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WizardryFlaskBridgeTest {

    @Test
    void refreshesEveryMutableWizardryCapacity() {
        int small = ItemManaFlask.Size.SMALL.capacity;
        int medium = ItemManaFlask.Size.MEDIUM.capacity;
        int large = ItemManaFlask.Size.LARGE.capacity;
        try {
            WizardryFlaskBridge.refreshCapacities(20, 50, 100);
            assertEquals(20, ItemManaFlask.Size.SMALL.capacity);
            assertEquals(50, ItemManaFlask.Size.MEDIUM.capacity);
            assertEquals(100, ItemManaFlask.Size.LARGE.capacity);
        } finally {
            WizardryFlaskBridge.refreshCapacities(small, medium, large);
        }
    }

    @Test
    void rejectsInvalidCapacityPublication() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WizardryFlaskBridge.refreshCapacities(-1, 50, 100)
        );
    }
}
