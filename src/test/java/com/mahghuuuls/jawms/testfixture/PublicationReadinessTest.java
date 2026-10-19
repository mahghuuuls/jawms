package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual scalar delivery-gate owner, not proof of transformed installation or a generated permission. */
class PublicationReadinessTest {
    private final UUID epoch = UUID.randomUUID();
    private ParticipantUseMessages.Close close() {
        return new ParticipantUseMessages.Close(epoch, 4, 7, EnumHand.MAIN_HAND,
                new ResourceLocation("fixture", "stream"), ParticipantUseMessages.CloseCause.NORMAL_FINISH, 9);
    }
    private WandPublicationRecord record(int kind, int window, long intent, long use, long generation, int slot) {
        return new WandPublicationRecord(epoch, 1, kind, window,
                new WandPublicationRecord.Entry(0, slot, generation, intent, use));
    }
    @Test void fullWindowAndDirectReadinessCannotBeSatisfiedByAnOrdinarySlotInstall() {
        for (PublicationReadiness.Boundary boundary : new PublicationReadiness.Boundary[] {
                PublicationReadiness.Boundary.WINDOW, PublicationReadiness.Boundary.DIRECT }) {
            PublicationReadiness gate = new PublicationReadiness(boundary, true);
            gate.pending(epoch, 4, EnumHand.MAIN_HAND, 0); gate.close(close()); gate.blocked(epoch, 4);
            gate.publication(record(WandPublicationRecord.SLOT, 0, 4, 7, 9, 36)); assertFalse(gate.ready());
            gate.publication(record(boundary == PublicationReadiness.Boundary.WINDOW ? WandPublicationRecord.WINDOW : WandPublicationRecord.SLOT,
                    boundary == PublicationReadiness.Boundary.WINDOW ? 0 : -2, 4, 7, 9,
                    boundary == PublicationReadiness.Boundary.WINDOW ? 36 : 0));
            assertTrue(gate.ready());
        }
    }
    @Test void setupRetirementOldUseWrongGenerationAndWrongSlotCannotReleaseCurrentClose() {
        PublicationReadiness gate = new PublicationReadiness(PublicationReadiness.Boundary.RETIREMENT, false);
        gate.publication(record(WandPublicationRecord.RETIRE, -1, 3, 2, 8, -1));
        gate.pending(epoch, 4, EnumHand.MAIN_HAND, 0); gate.close(close()); assertFalse(gate.ready());
        for (long[] bad : new long[][] { {3, 7, 9}, {4, 6, 9}, {4, 7, 8} }) {
            gate.publication(record(WandPublicationRecord.RETIRE, -1, bad[0], bad[1], bad[2], -1)); assertFalse(gate.ready());
        }
        gate.publication(new WandPublicationRecord(epoch, 2, WandPublicationRecord.RETIRE, -1,
                new WandPublicationRecord.Entry(1, -1, 9, 4, 7))); assertFalse(gate.ready());
        gate.publication(record(WandPublicationRecord.RETIRE, -1, 4, 7, 9, -1)); assertTrue(gate.ready());
    }
    @Test void actualInstallMayPrecedeCloseButPhysicalBlockedPressAndExactPendingStillMatter() {
        PublicationReadiness gate = new PublicationReadiness(PublicationReadiness.Boundary.SLOT, true);
        gate.pending(epoch, 4, EnumHand.MAIN_HAND, 0);
        gate.publication(record(WandPublicationRecord.SLOT, 0, 4, 7, 9, 36)); assertFalse(gate.ready());
        gate.close(close()); assertFalse(gate.ready());
        gate.blocked(UUID.randomUUID(), 4); gate.blocked(epoch, 3); assertFalse(gate.ready());
        gate.blocked(epoch, 4); assertTrue(gate.ready());
        gate.pending(epoch, 5, EnumHand.MAIN_HAND, 0); assertFalse(gate.ready());
    }
    @Test void closeBeforePendingAndContextNotificationStillRequireExactTarget() {
        PublicationReadiness gate = new PublicationReadiness(PublicationReadiness.Boundary.CONTEXT, false);
        gate.close(close()); gate.context(epoch, 4); assertFalse(gate.ready());
        gate.pending(epoch, 4, EnumHand.MAIN_HAND, 0); assertFalse(gate.ready());
        gate.context(epoch, 3); assertFalse(gate.ready()); gate.context(epoch, 4); assertTrue(gate.ready());
    }
}
