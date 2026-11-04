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

        com.mahghuuuls.jawms.proxy.CommonProxy previous =
                com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.proxy;
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
            ClientManaState.acceptManaged(managedView(3, true), active, active);
            assertFalse(ClientManaState.isManagedHeld(active));
            assertTrue(ClientManaState.isAvailable());
            assertEquals(100, ClientManaState.getCurrentMana());
        } finally {
            com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.proxy = previous;
        }
        assertTrue(ClientManaState.acceptsLegacy(old, old));
    }

    private static com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage managedView(long sequence, boolean ready) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            buffer.writeLong(sequence);
            buffer.writeLong(1);
            buffer.writeLong(2);
            buffer.writeByte(ready ? 2 : 0);
            if (ready) new com.mahghuuuls.jawms.internal.mana.ManaStateMessage(100, 100, 0, 1).toBytes(buffer);
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
        ClientManaState.accept(40, 50, 2.0D, 4);
        ClientManaState.accept(30, 50, 3.0D, 3);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(40, ClientManaState.getCurrentMana());
        assertEquals(2.0D, ClientManaState.getEffectiveRegeneration());
        ClientManaSnapshot first = ClientManaState.snapshot();
        assertEquals(40, first.getCurrentMana());
        assertEquals(50, first.getMaximumMana());
        assertEquals(2.0D, first.getEffectiveRegeneration());
        assertTrue(first.hasKnownRevision());
        assertEquals(4L, first.getRevision());

        ClientManaState.accept(10, 50, 4.0D, 4);
        assertEquals(first, ClientManaState.snapshot());

        ClientManaState.accept(20, 50, 3.0D, 5);
        assertEquals(20, ClientManaState.getCurrentMana());
        assertEquals(3.0D, ClientManaState.getEffectiveRegeneration());
        assertEquals(5L, ClientManaState.snapshot().getRevision());
    }

    @Test
    void clearingAllowsARevisionResetOnReconnect() {
        ClientManaState.accept(40, 50, 2.0D, 20);
        ClientManaState.clear();
        ClientManaState.accept(30, 50, 2.0D, 1);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(30, ClientManaState.getCurrentMana());
        assertEquals(50, ClientManaState.getMaximumMana());
        assertEquals(1L, ClientManaState.snapshot().getRevision());
    }

    @Test
    void clearRemovesDisplayState() {
        ClientManaState.accept(40, 50, 2.0D, 1);

        ClientManaState.clear();

        assertFalse(ClientManaState.isAvailable());
        assertFalse(ClientManaState.snapshot().isAvailable());
        assertFalse(ClientManaState.snapshot().hasKnownRevision());
        assertEquals(ClientManaSnapshot.UNKNOWN_REVISION, ClientManaState.snapshot().getRevision());
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
