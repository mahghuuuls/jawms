package com.mahghuuuls.jawms.client;

import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientManaStateTest {

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

        ClientManaState.accept(20, 50, 3.0D, 5);
        assertEquals(20, ClientManaState.getCurrentMana());
        assertEquals(3.0D, ClientManaState.getEffectiveRegeneration());
    }

    @Test
    void clearingAllowsARevisionResetOnReconnect() {
        ClientManaState.accept(40, 50, 2.0D, 20);
        ClientManaState.clear();
        ClientManaState.accept(30, 50, 2.0D, 1);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(30, ClientManaState.getCurrentMana());
        assertEquals(50, ClientManaState.getMaximumMana());
    }

    @Test
    void clearRemovesDisplayState() {
        ClientManaState.accept(40, 50, 2.0D, 1);

        ClientManaState.clear();

        assertFalse(ClientManaState.isAvailable());
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
