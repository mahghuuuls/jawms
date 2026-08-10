package com.mahghuuuls.jawms.api.client;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertFalse(snapshot.hasKnownRevision());
        assertEquals(ClientManaSnapshot.UNKNOWN_REVISION, snapshot.getRevision());
    }

    @Test
    void availableSnapshotValidatesAndRetainsWholeState() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D);

        assertTrue(snapshot.isAvailable());
        assertEquals(25, snapshot.getCurrentMana());
        assertEquals(60, snapshot.getMaximumMana());
        assertEquals(2.4D, snapshot.getEffectiveRegeneration());
        assertFalse(snapshot.hasKnownRevision());
        assertEquals(ClientManaSnapshot.UNKNOWN_REVISION, snapshot.getRevision());

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
    void revisionAwareSnapshotValidatesAndRetainsRevision() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D, 7L);

        assertTrue(snapshot.hasKnownRevision());
        assertEquals(7L, snapshot.getRevision());
        assertThrows(IllegalArgumentException.class,
                () -> ClientManaSnapshot.available(25, 60, 2.4D, -1L));
    }

    @Test
    void equalityHashingAndDiagnosticsUseOnlyPublicState() {
        ClientManaSnapshot first = ClientManaSnapshot.available(25, 60, 2.4D, 7L);
        ClientManaSnapshot same = ClientManaSnapshot.available(25, 60, 2.4D, 7L);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, ClientManaSnapshot.available(24, 60, 2.4D, 7L));
        assertNotEquals(first, ClientManaSnapshot.available(25, 61, 2.4D, 7L));
        assertNotEquals(first, ClientManaSnapshot.available(25, 60, 2.5D, 7L));
        assertNotEquals(first, ClientManaSnapshot.available(25, 60, 2.4D, 8L));
        assertNotEquals(first, ClientManaSnapshot.available(25, 60, 2.4D));
        assertEquals(
                "ClientManaSnapshot{available=true, currentMana=25, maximumMana=60, "
                        + "effectiveRegeneration=2.4, revision=7}",
                first.toString()
        );
        assertEquals(
                "ClientManaSnapshot{available=false, currentMana=0, maximumMana=0, "
                        + "effectiveRegeneration=0.0, revision=unknown}",
                ClientManaSnapshot.unavailable().toString()
        );
    }

    @Test
    void legacyHudEventConstructorRetainsDescriptorsAndUsesUnknownContext() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D);
        ManaHudRenderEvent event = new ManaHudRenderEvent(snapshot, 0.5F, 320, 180);

        // Forge's runtime EventTransformer generates isCancelable() for annotated
        // events. Plain unit tests intentionally run without that transformer.
        assertTrue(ManaHudRenderEvent.class.isAnnotationPresent(Cancelable.class));
        assertSame(snapshot, event.getSnapshot());
        assertEquals(0.5F, event.getPartialTicks());
        assertEquals(320, event.getScaledWidth());
        assertEquals(180, event.getScaledHeight());
        assertFalse(event.getContext().isCreativeModeKnown());
        assertThrows(NullPointerException.class,
                () -> new ManaHudRenderEvent(null, Float.NaN, -1, -1));
    }

    @Test
    void hudEventCarriesOneImmutableContext() {
        ClientManaSnapshot snapshot = ClientManaSnapshot.available(25, 60, 2.4D, 7L);
        ClientHudContext context = ClientHudContext.known(true, 0.5F, 320, 180);
        ManaHudRenderEvent event = new ManaHudRenderEvent(snapshot, context);

        assertSame(snapshot, event.getSnapshot());
        assertSame(context, event.getContext());
        assertTrue(event.getContext().isCreativeMode());
        assertEquals(context.getPartialTicks(), event.getPartialTicks());
        assertEquals(context.getScaledWidth(), event.getScaledWidth());
        assertEquals(context.getScaledHeight(), event.getScaledHeight());
        assertThrows(NullPointerException.class, () -> new ManaHudRenderEvent(null, context));
        assertThrows(NullPointerException.class, () -> new ManaHudRenderEvent(snapshot, null));
    }
}
