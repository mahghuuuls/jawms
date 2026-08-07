package com.mahghuuuls.jawms.api.client;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientManaSnapshotTest {

    @Test
    void unavailableSnapshotIsSharedAndNeutral() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.unavailable();

        assertSame(snapshot, ClientManaSnapshot.unavailable());
        assertFalse(snapshot.isAvailable());
        assertEquals(0, snapshot.getCurrentMana());
        assertEquals(0, snapshot.getMaximumMana());
        assertEquals(0.0D, snapshot.getEffectiveRegeneration());
    }

    @Test
    void availableSnapshotValidatesAndRetainsWholeState() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D);

        assertTrue(snapshot.isAvailable());
        assertEquals(25, snapshot.getCurrentMana());
        assertEquals(60, snapshot.getMaximumMana());
        assertEquals(2.4D, snapshot.getEffectiveRegeneration());

        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(-1, 60, 2.4D));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(61, 60, 2.4D));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(0, 0, 2.4D));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(0, 60, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(0, 60, -0.1D));
    }

    @Test
    void hudEventIsCancelableAndCarriesOnlyCapturedValues() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D);
        ManaHudRenderEvent event = new ManaHudRenderEvent(snapshot, 0.5F, 320, 180);

        // Forge's runtime EventTransformer generates isCancelable() for annotated
        // events. Plain unit tests intentionally run without that transformer.
        assertTrue(ManaHudRenderEvent.class.isAnnotationPresent(Cancelable.class));
        assertSame(snapshot, event.getSnapshot());
        assertEquals(0.5F, event.getPartialTicks());
        assertEquals(320, event.getScaledWidth());
        assertEquals(180, event.getScaledHeight());
    }
}
