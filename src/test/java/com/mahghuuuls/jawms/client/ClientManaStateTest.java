package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientManaStateTest {

    @Test
    void snapshotIsUnavailableWhenTheConnectionDisappearsBeforeCleanup() {
        Object connection = new Object();
        assertTrue(ClientManaState.acceptsLegacy(connection, connection));
        ClientManaState.accept(110, 120, 0, 20);
        assertEquals(110, ClientManaState.snapshot(connection).getCurrentMana());

        assertEquals(ClientManaSnapshot.unavailable(), ClientManaState.snapshot(null));
        assertFalse(ClientManaState.snapshot(null).hasKnownRevision());
    }

    @Test
    void replacementConnectionCannotReadOrAcceptTheOldSnapshot() {
        Object previous = new Object();
        Object replacement = new Object();
        assertTrue(ClientManaState.acceptsLegacy(previous, previous));
        ClientManaState.accept(110, 120, 0, 20);

        assertEquals(ClientManaSnapshot.unavailable(), ClientManaState.snapshot(replacement));
        assertFalse(ClientManaState.acceptsLegacy(previous, replacement));
        ClientManaState.acceptManaged(managedView(21, true), previous, replacement);
        assertEquals(ClientManaSnapshot.unavailable(), ClientManaState.snapshot(replacement));

        assertTrue(ClientManaState.acceptsLegacy(replacement, replacement));
        ClientManaState.accept(100, 120, 0, 1);
        assertEquals(100, ClientManaState.snapshot(replacement).getCurrentMana());
        assertEquals(1L, ClientManaState.snapshot(replacement).getRevision());
        assertEquals(ClientManaSnapshot.unavailable(), ClientManaState.snapshot(previous));
    }

    @Test
    void managedHoldRejectsAnOlderViewAndAnObsoleteHandler() {
        Object old = new Object();
        Object active = new Object();
        com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage pending = managedView(2, false);
        com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage accepted = managedView(1, true);
        ClientManaState.accept(100, 100, 0, 1);

        ClientManaState.acceptManaged(pending, active, active);
        ClientManaState.acceptManaged(accepted, active, active);
        ClientManaState.acceptManaged(accepted, old, active);
        assertTrue(ClientManaState.isManagedHeld(active));
        assertFalse(ClientManaState.isAvailable());
        assertFalse(ClientManaState.acceptsLegacy(active, active));
        assertFalse(ClientManaState.acceptsLegacy(old, active));

        withClientSink(() -> {
            ClientManaState.acceptManaged(managedView(3, true), active, active);
            assertFalse(ClientManaState.isManagedHeld(active));
            assertTrue(ClientManaState.isAvailable());
            assertEquals(100, ClientManaState.getCurrentMana());
        });
        assertTrue(ClientManaState.acceptsLegacy(old, old));
    }

    @Test
    void sameConnectionLifecycleRejectsDelayedSourceReadyUntilTheDestinationCompletes() {
        Object active = new Object();
        Object obsolete = new Object();
        withClientSink(() -> {
            ClientManaState.acceptManaged(managedView(8, 1, true, 110, 20), active, active);
            assertEquals(110, ClientManaState.snapshot(active).getCurrentMana());
            ClientManaState.invalidateLifecycle(obsolete, active);
            assertTrue(ClientManaState.snapshot(active).isAvailable());

            ClientManaState.invalidateLifecycle(active, active);
            assertTrue(ClientManaState.isManagedHeld(active));
            assertFalse(ClientManaState.snapshot(active).isAvailable());
            assertEquals(1.0D, ClientManaState.getSpellCostMultiplier(Element.FIRE));
            ClientManaState.acceptManaged(managedView(7, 1, true, 110, 19), active, active);
            ClientManaState.acceptManaged(managedView(9, 1, true, 110, 21), active, active);
            assertFalse(ClientManaState.snapshot(active).isAvailable());

            ClientManaState.acceptManaged(managedView(10, 2, false, 0, 0), active, active);
            ClientManaState.acceptManaged(managedView(11, 1, true, 110, 22), active, active);
            assertFalse(ClientManaState.snapshot(active).isAvailable());
            ClientManaState.acceptManaged(managedView(12, 2, true, 100, 1), active, active);
            assertFalse(ClientManaState.isManagedHeld(active));
            assertEquals(100, ClientManaState.snapshot(active).getCurrentMana());
            assertEquals(1, ClientManaState.snapshot(active).getRevision());
        });
    }

    private static void withClientSink(Runnable action) {
        com.mahghuuuls.jawms.proxy.CommonProxy previous = com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.proxy;
        com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.proxy = new com.mahghuuuls.jawms.proxy.CommonProxy() {
            @Override
            public void acceptManaState(int current, int maximum, double regeneration, long revision,
                    double efficiency, java.util.Map<Element, Double> efficiencies,
                    double multiplier, java.util.Map<Element, Double> multipliers) {
                ClientManaState.accept(current, maximum, regeneration, revision, efficiency,
                        efficiencies, multiplier, multipliers);
            }
        };
        try {
            action.run();
        } finally {
            com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.proxy = previous;
        }
    }

    private static com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage managedView(long sequence, boolean ready) {
        return managedView(sequence, 1, ready, 100, 1);
    }

    private static com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage managedView(
            long sequence, long generation, boolean ready, int current, long revision) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            buffer.writeLong(sequence);
            buffer.writeLong(generation);
            buffer.writeLong(2);
            buffer.writeByte(ready ? 2 : 0);
            if (ready) new com.mahghuuuls.jawms.internal.mana.ManaStateMessage(current, 120, 0, revision).toBytes(buffer);
            com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage view =
                    new com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage();
            view.fromBytes(buffer);
            return view;
        } finally {
            buffer.release();
        }
    }

    @AfterEach
    void reset() {
        ClientManaState.clear();
    }

    @Test
    void acceptsOnlyNewerRevisionsWithinASession() {
        Object connection = new Object();
        assertTrue(ClientManaState.acceptsLegacy(connection, connection));
        ClientManaState.accept(40, 50, 2.0D, 4);
        ClientManaState.accept(30, 50, 3.0D, 3);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(40, ClientManaState.getCurrentMana());
        assertEquals(2.0D, ClientManaState.getEffectiveRegeneration());
        ClientManaSnapshot first = ClientManaState.snapshot(connection);
        assertEquals(40, first.getCurrentMana());
        assertEquals(50, first.getMaximumMana());
        assertEquals(2.0D, first.getEffectiveRegeneration());
        assertTrue(first.hasKnownRevision());
        assertEquals(4L, first.getRevision());

        ClientManaState.accept(10, 50, 4.0D, 4);
        assertEquals(first, ClientManaState.snapshot(connection));

        ClientManaState.accept(20, 50, 3.0D, 5);
        assertEquals(20, ClientManaState.getCurrentMana());
        assertEquals(3.0D, ClientManaState.getEffectiveRegeneration());
        assertEquals(5L, ClientManaState.snapshot(connection).getRevision());
    }

    @Test
    void clearingAllowsARevisionResetOnReconnect() {
        Object connection = new Object();
        assertTrue(ClientManaState.acceptsLegacy(connection, connection));
        ClientManaState.accept(40, 50, 2.0D, 20);
        ClientManaState.clear();
        ClientManaState.accept(30, 50, 2.0D, 1);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(30, ClientManaState.getCurrentMana());
        assertEquals(50, ClientManaState.getMaximumMana());
        assertEquals(1L, ClientManaState.snapshot(connection).getRevision());
    }

    @Test
    void clearRemovesDisplayState() {
        Object connection = new Object();
        assertTrue(ClientManaState.acceptsLegacy(connection, connection));
        ClientManaState.accept(40, 50, 2.0D, 1);

        ClientManaState.clear();

        assertFalse(ClientManaState.isAvailable());
        assertFalse(ClientManaState.snapshot(connection).isAvailable());
        assertFalse(ClientManaState.snapshot(connection).hasKnownRevision());
        assertEquals(ClientManaSnapshot.UNKNOWN_REVISION, ClientManaState.snapshot(connection).getRevision());
        assertEquals(1.0D, ClientManaState.getSpellCostMultiplier(Element.FIRE));
    }

    @Test
    void resolvesSynchronizedGlobalAndElementCostMultipliers() {
        EnumMap<Element, Double> elements = new EnumMap<>(Element.class);
        elements.put(Element.FIRE, 1.5D);
        ClientManaState.accept(
                40, 50, 2.0D, 1, 0.0D, Collections.emptyMap(), 0.5D, elements
        );

        assertEquals(1.5D, ClientManaState.getSpellCostMultiplier(Element.FIRE));
        assertEquals(0.5D, ClientManaState.getSpellCostMultiplier(Element.ICE));
    }
}
