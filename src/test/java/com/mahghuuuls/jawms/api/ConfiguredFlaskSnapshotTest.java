package com.mahghuuuls.jawms.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredFlaskSnapshotTest {

    @Test
    void unavailableSnapshotIsSharedAndExplicit() {
        ConfiguredFlaskSnapshot snapshot = ConfiguredFlaskSnapshot.unavailable();

        assertSame(snapshot, ConfiguredFlaskSnapshot.unavailable());
        assertFalse(snapshot.isAvailable());
        assertFalse(snapshot.hasKnownRevision());
        assertEquals(ConfiguredFlaskSnapshot.UNKNOWN_REVISION, snapshot.getRevision());
        assertEquals(0, snapshot.getSmallRestoration());
        assertEquals(0, snapshot.getMediumRestoration());
        assertEquals(0, snapshot.getLargeRestoration());
    }

    @Test
    void availableSnapshotValidatesAndIncludesEveryFieldInValueSemantics() {
        ConfiguredFlaskSnapshot first = ConfiguredFlaskSnapshot.available(20, 50, 100, 7L);
        ConfiguredFlaskSnapshot same = ConfiguredFlaskSnapshot.available(20, 50, 100, 7L);

        assertTrue(first.isAvailable());
        assertTrue(first.hasKnownRevision());
        assertEquals(7L, first.getRevision());
        assertEquals(20, first.getSmallRestoration());
        assertEquals(50, first.getMediumRestoration());
        assertEquals(100, first.getLargeRestoration());
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, ConfiguredFlaskSnapshot.available(21, 50, 100, 7L));
        assertNotEquals(first, ConfiguredFlaskSnapshot.available(20, 51, 100, 7L));
        assertNotEquals(first, ConfiguredFlaskSnapshot.available(20, 50, 101, 7L));
        assertNotEquals(first, ConfiguredFlaskSnapshot.available(20, 50, 100, 8L));
        assertNotEquals(first, ConfiguredFlaskSnapshot.unavailable());
        assertEquals(
                "ConfiguredFlaskSnapshot{available=true, smallRestoration=20, "
                        + "mediumRestoration=50, largeRestoration=100, revision=7}",
                first.toString()
        );
    }

    @Test
    void invalidAvailableValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguredFlaskSnapshot.available(-1, 50, 100, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguredFlaskSnapshot.available(20, -1, 100, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguredFlaskSnapshot.available(20, 50, -1, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguredFlaskSnapshot.available(20, 50, 100, -1L));
    }
}
