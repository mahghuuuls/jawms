package com.mahghuuuls.jawms.internal.casting;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Production network-local attachment state, not a transformed connection or vanilla apply test. */
class WandPublicationReceiverTest {
    private final UUID epoch = UUID.randomUUID();
    private WandPublicationReceiver<Object> receiver() {
        WandPublicationReceiver<Object> result = new WandPublicationReceiver<>(); result.capability(epoch); return result;
    }
    private WandPublicationRecord record(UUID epoch, long serial) {
        return new WandPublicationRecord(epoch, serial, WandPublicationRecord.SLOT, 0,
                new WandPublicationRecord.Entry(0, 36, 1, 2, 3));
    }
    @Test void exactDecodedInstanceSurvivesSchedulingUntilConsumedOnce() {
        WandPublicationReceiver<Object> receiver = receiver();
        Object packet = new String("packet");
        WandPublicationRecord record = record(epoch, 1);
        assertTrue(receiver.record(record));
        assertEquals(1, receiver.pending());
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(packet, (m, p) -> true));
        assertNull(receiver.take(new String("packet")));
        assertSame(record, receiver.take(packet));
        assertNull(receiver.take(packet)); assertEquals(0, receiver.pending());
    }
    @Test void oldEpochCannotFaultOrBootstrapCurrentConnection() {
        WandPublicationReceiver<Object> receiver = receiver();
        assertTrue(receiver.record(record(UUID.randomUUID(), 99)));
        assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(new Object(), (m, p) -> true));
        assertFalse(receiver.faulted());
        assertTrue(receiver.record(record(epoch, 1)));
        Object packet = new Object(); receiver.packet(packet, (m, p) -> true);
        assertNotNull(receiver.take(packet));
    }
    @Test void staleAndDuplicateSerialCannotReapplyPairedInventorySnapshot() {
        WandPublicationReceiver<Object> receiver = receiver();
        receiver.record(record(epoch, 2));
        Object packet = new Object(); receiver.packet(packet, (m, p) -> true); receiver.take(packet);
        for (long stale : new long[] { 1, 2 }) {
            receiver.record(record(epoch, stale));
            Object duplicate = new Object();
            assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(duplicate, (m, p) -> true));
            assertNull(receiver.take(duplicate));
        }
    }
    @Test void interposedOrMissingPartnerFaultsButDoesNotFreezeOrdinaryPackets() {
        WandPublicationReceiver<Object> receiver = receiver(); receiver.record(record(epoch, 1));
        Object other = new Object();
        assertEquals(WandPublicationReceiver.Delivery.FAULT, receiver.packet(other, (m, p) -> false));
        assertTrue(receiver.faulted()); assertEquals(0, receiver.pending());
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(new Object(), (m, p) -> true));
        assertFalse(receiver.record(record(epoch, 2)));
        receiver.capability(UUID.randomUUID()); assertTrue(receiver.faulted());
        receiver = receiver(); receiver.record(record(epoch, 1));
        assertFalse(receiver.record(record(epoch, 2))); assertTrue(receiver.faulted());
    }
    @Test void faultedSessionDiscardsStalePairsButDeliversFreshSnapshotsWithoutProvenance() {
        WandPublicationReceiver<Object> receiver = receiver();
        receiver.record(record(epoch, 2));
        Object applied = new Object(); receiver.packet(applied, (m, p) -> true); receiver.take(applied);
        receiver.fault();
        boolean freshSeen = false;
        for (long serial : new long[] { 1, 2, 3, 3 }) {
            assertFalse(receiver.record(record(epoch, serial)));
            Object packet = new Object();
            assertEquals(serial == 3 && !freshSeen ? WandPublicationReceiver.Delivery.APPLY : WandPublicationReceiver.Delivery.DISCARD,
                    receiver.packet(packet, (m, p) -> true));
            if (serial == 3) freshSeen = true;
            assertNull(receiver.take(packet));
            assertEquals(0, receiver.pending());
            assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(new Object(), (m, p) -> true));
        }
        receiver.capability(UUID.randomUUID()); assertTrue(receiver.faulted());
    }
    @Test void exactCapacityPassesAndNextAssociationFaultsAndReleasesReferences() {
        WandPublicationReceiver<Object> receiver = receiver();
        Object[] packets = new Object[WandPublicationRecord.MAX_PENDING];
        for (int i = 0; i < packets.length; i++) {
            assertTrue(receiver.record(record(epoch, i + 1)));
            assertEquals(i + 1, receiver.pending());
            packets[i] = new Object(); receiver.packet(packets[i], (m, p) -> true);
        }
        assertEquals(256, receiver.pending()); assertFalse(receiver.faulted());
        assertFalse(receiver.record(record(epoch, 257))); assertTrue(receiver.faulted());
        assertEquals(1, receiver.pending()); assertNull(receiver.take(packets[0]));
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(new Object(), (m, p) -> true));
        assertEquals(0, receiver.pending());
    }
    @Test void retirementHasNoPartnerAndClearRequiresNewCapability() {
        WandPublicationReceiver<Object> receiver = receiver();
        WandPublicationRecord retired = new WandPublicationRecord(epoch, 1, WandPublicationRecord.RETIRE, -1,
                new WandPublicationRecord.Entry(0, -1, 1, 2, 3));
        assertTrue(receiver.record(retired)); assertEquals(0, receiver.pending());
        assertFalse(receiver.record(retired));
        receiver.clear();
        receiver.record(record(epoch, 1));
        assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(new Object(), (m, p) -> true));
    }
    @Test void mainThreadFaultCleanupCannotLoseAnAlreadyKnownStalePair() {
        WandPublicationReceiver<Object> receiver = receiver();
        receiver.record(record(epoch, 2));
        Object applied = new Object(); receiver.packet(applied, (m, p) -> true); receiver.take(applied);
        receiver.fault(); receiver.record(record(epoch, 1));
        receiver.fault(); // Later main-thread retirement after network fault notification.
        assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(new Object(), (m, p) -> true));
        assertEquals(0, receiver.pending()); assertTrue(receiver.faulted());
    }
    @Test void interposedRetirementConsumesBrokenPairWithoutLeavingAStaleDispositionOnFuturePackets() {
        WandPublicationReceiver<Object> receiver = receiver();
        receiver.record(record(epoch, 2));
        Object applied = new Object(); receiver.packet(applied, (m, p) -> true); receiver.take(applied);
        receiver.fault(); receiver.record(record(epoch, 1));
        assertFalse(receiver.record(new WandPublicationRecord(epoch, 3, WandPublicationRecord.RETIRE, -1,
                new WandPublicationRecord.Entry(0, -1, 1, 2, 3))));
        assertEquals(0, receiver.pending()); // Metadata is consumed at fault, not retained as history.
        Object orphan = new Object();
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(orphan, (m, p) -> true));
        assertNull(receiver.take(orphan)); // An unidentifiable orphan cannot grant a handoff.
        receiver.record(record(epoch, 1));
        assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(new Object(), (m, p) -> true));
        receiver.record(record(epoch, 4));
        Object fresh = new Object();
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(fresh, (m, p) -> true));
        assertNull(receiver.take(fresh));
    }
    @Test void interposedSecondMarkerOwnsItsDispositionButCannotRestoreProvenance() {
        WandPublicationReceiver<Object> receiver = receiver();
        receiver.record(record(epoch, 2));
        receiver.record(record(epoch, 1)); // Replaces the broken association with an intact stale pair.
        assertTrue(receiver.faulted()); assertEquals(1, receiver.pending());
        assertEquals(WandPublicationReceiver.Delivery.DISCARD, receiver.packet(new Object(), (m, p) -> true));
        receiver.record(record(epoch, 3)); receiver.record(record(epoch, 4));
        Object fresh = new Object();
        assertEquals(WandPublicationReceiver.Delivery.APPLY, receiver.packet(fresh, (m, p) -> true));
        assertNull(receiver.take(fresh)); assertEquals(0, receiver.pending());
    }
}
