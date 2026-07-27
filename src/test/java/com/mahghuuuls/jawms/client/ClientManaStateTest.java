package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        ClientManaState.accept(900, 1000, 4);
        ClientManaState.accept(800, 1000, 3);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(900, ClientManaState.getCurrentMana());

        ClientManaState.accept(700, 1000, 5);
        assertEquals(700, ClientManaState.getCurrentMana());
    }

    @Test
    void clearingAllowsARevisionResetOnReconnect() {
        ClientManaState.accept(900, 1000, 20);
        ClientManaState.clear();
        ClientManaState.accept(600, 1000, 1);

        assertTrue(ClientManaState.isAvailable());
        assertEquals(600, ClientManaState.getCurrentMana());
        assertEquals(1000, ClientManaState.getMaximumMana());
    }

    @Test
    void clearRemovesDisplayState() {
        ClientManaState.accept(900, 1000, 1);

        ClientManaState.clear();

        assertFalse(ClientManaState.isAvailable());
    }
}
