package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.*;

class ParticipantUseProtocolTest {
    private final UUID epoch = UUID.randomUUID();
    private final ResourceLocation spell = new ResourceLocation("fixture", "continuous");
    private final Object stack = new Object();

    @Test void serverBindsOnlyAnnouncedHandAndReusesHeldIdentity() {
        ParticipantUseState state = new ParticipantUseState(epoch);
        assertNull(state.bind(EnumHand.MAIN_HAND, spell, stack, 1));
        assertFalse(state.intent(UUID.randomUUID(), 1, EnumHand.MAIN_HAND, spell));
        assertTrue(state.intent(epoch, 1, EnumHand.MAIN_HAND, spell));
        // Main-hand candidate did not start a use, so the vanilla offhand candidate may bind.
        assertTrue(state.intent(epoch, 1, EnumHand.OFF_HAND, spell));
        assertNull(state.bind(EnumHand.MAIN_HAND, spell, stack, 1));
        ParticipantUseState.Use use = state.bind(EnumHand.OFF_HAND, spell, stack, 2);
        assertTrue(state.matchesOpen(use, EnumHand.OFF_HAND, spell, stack));
        assertFalse(state.matchesOpen(use, EnumHand.OFF_HAND, spell, new Object()));
        assertFalse(state.matchesOpen(use, EnumHand.MAIN_HAND, spell, stack));
        assertFalse(state.matchesOpen(use, EnumHand.OFF_HAND, new ResourceLocation("fixture", "changed"), stack));
        assertSame(use, state.bind(EnumHand.OFF_HAND, spell, stack, 3));
        assertFalse(state.intent(epoch, 1, EnumHand.MAIN_HAND, spell));
        assertFalse(state.intent(epoch, 2, EnumHand.OFF_HAND, spell));
        assertTrue(state.close(use, CloseCause.TERMINAL));
        assertFalse(state.matchesOpen(use, EnumHand.OFF_HAND, spell, stack));
        assertNull(use.stack); // A close tombstone cannot retain the owning stack.
        assertTrue(state.retryBlocked());
        assertFalse(state.release(UUID.randomUUID(), 1));
        assertTrue(state.retryBlocked());
        assertTrue(state.release(epoch, 1));
        assertFalse(state.retryBlocked());
        assertNull(state.bind(EnumHand.OFF_HAND, spell, stack, 4));
        assertTrue(state.intent(epoch, 2, EnumHand.OFF_HAND, spell));
        assertNotSame(use, state.bind(EnumHand.OFF_HAND, spell, stack, 5));
        assertFalse(state.close(use, CloseCause.TERMINAL));
    }

    @Test void serverReleaseWatermarkDoesNotUnblockANewerUse() {
        ParticipantUseState state = new ParticipantUseState(epoch);
        state.intent(epoch, 1, EnumHand.MAIN_HAND, spell);
        ParticipantUseState.Use old = state.bind(EnumHand.MAIN_HAND, spell, stack, 1);
        assertTrue(state.release(epoch, 1));
        assertTrue(state.close(old, CloseCause.TERMINAL));
        assertFalse(state.retryBlocked());
        state.intent(epoch, 2, EnumHand.MAIN_HAND, spell);
        ParticipantUseState.Use current = state.bind(EnumHand.MAIN_HAND, spell, stack, 2);
        state.close(current, CloseCause.TERMINAL);
        assertFalse(state.release(epoch, 1));
        assertTrue(state.retryBlocked());
        assertFalse(state.release(epoch, 3));
        assertTrue(state.release(epoch, 2));
        assertFalse(state.retryBlocked());
    }

    private ClientUseState client() {
        ClientUseState state = new ClientUseState();
        state.capability(epoch);
        assertEquals(1, state.input(EnumHand.MAIN_HAND, spell));
        return state;
    }
    private Close close(long intent, long use, CloseCause cause) {
        return new Close(epoch, intent, use, EnumHand.MAIN_HAND, spell, cause, 17);
    }
    @Test void boundReleaseWithoutAnItemCallbackStillBlocksAndTimesOut() {
        ClientUseState state = client(); state.bind(EnumHand.MAIN_HAND, spell);
        assertEquals(1, state.release());
        assertTrue(state.pending()); assertTrue(state.blocked());
        assertEquals(0, state.input(EnumHand.MAIN_HAND, spell));
        assertFalse(state.elapse(ClientUseState.CONFIRMATION_NANOS - 1));
        assertEquals(0, state.release());
        assertTrue(state.elapse(1)); assertTrue(state.faulted());
    }
    @Test void callbackAttachmentCannotRestartTheReleaseDeadline() {
        ClientUseState state = client(); state.bind(EnumHand.MAIN_HAND, spell); state.release();
        assertFalse(state.elapse(ClientUseState.CONFIRMATION_NANOS - 1));
        assertTrue(state.capture(EnumHand.MAIN_HAND, spell));
        assertEquals(1, state.remainingNanos()); assertTrue(state.elapse(1));
    }
    @Test void resolvedCallbackFirstOrNoCallbackBoundaryCannotReopenOnReleaseOrStrayCapture() {
        for (boolean callbackFirst : new boolean[] { false, true }) {
            ClientUseState state = client(); state.bind(EnumHand.MAIN_HAND, spell);
            if (callbackFirst) assertTrue(state.capture(EnumHand.MAIN_HAND, spell)); else state.release();
            assertTrue(state.accept(close(1, 7, CloseCause.NORMAL_FINISH)));
            assertTrue(state.ready()); state.resolved();
            state.release();
            assertFalse(state.pending()); assertFalse(state.capture(EnumHand.MAIN_HAND, spell));
            assertEquals(2, state.input(EnumHand.MAIN_HAND, spell));
        }
    }
    @Test void alreadyArrivedNormalCloseStillAllowsSynchronousReleaseCallbackAttachment() {
        ClientUseState state = client(); state.bind(EnumHand.MAIN_HAND, spell);
        assertTrue(state.accept(close(1, 7, CloseCause.NORMAL_FINISH)));
        state.release();
        assertTrue(state.ready()); // Controller release HEAD must not consume this before ItemWand's callback.
        assertTrue(state.capture(EnumHand.MAIN_HAND, spell)); assertTrue(state.finishPermitted());
        state.resolved(); assertFalse(state.pending());
    }
    @Test void clientNormalPermissionAfterReleaseIsNotDiscardedByWatermark() {
        ClientUseState state = client();
        assertEquals(1, state.release());
        assertTrue(state.capture(EnumHand.MAIN_HAND, spell));
        assertTrue(state.blocked());
        assertEquals(0, state.input(EnumHand.MAIN_HAND, spell));
        assertFalse(state.finishPermitted());
        assertTrue(state.accept(close(1, 7, CloseCause.NORMAL_FINISH)));
        assertTrue(state.finishPermitted());
        state.resolved();
        assertFalse(state.capture(EnumHand.MAIN_HAND, spell));
        assertTrue(state.blocked()); // Rejected held press was consumed, not queued.
        assertEquals(0, state.input(EnumHand.MAIN_HAND, spell));
        assertEquals(0, state.release()); // Fresh release clears only the discarded gesture.
        assertFalse(state.blocked());
        assertEquals(2, state.input(EnumHand.MAIN_HAND, spell));
        assertFalse(state.accept(close(1, 7, CloseCause.TERMINAL)));
    }
    @Test void clientTerminalBeforeOrAfterReleaseNeverPermitsFinishOrSticksOldBlock() {
        for (boolean releaseFirst : new boolean[] { false, true }) {
            ClientUseState state = client();
            if (releaseFirst) state.release();
            state.capture(EnumHand.MAIN_HAND, spell);
            assertTrue(state.accept(close(1, 1, CloseCause.TERMINAL)));
            assertFalse(state.finishPermitted());
            assertTrue(state.ready()); state.resolved();
            assertEquals(!releaseFirst, state.blocked());
            state.release();
            assertEquals(2, state.input(EnumHand.MAIN_HAND, spell));
        }
    }
    @Test void clientOnlyExactCloseCanResolveOnePendingCapture() {
        ClientUseState state = client();
        state.release(); state.capture(EnumHand.MAIN_HAND, spell);
        assertFalse(state.accept(new Close(UUID.randomUUID(), 1, 1, EnumHand.MAIN_HAND, spell, CloseCause.NORMAL_FINISH, 17)));
        assertFalse(state.accept(close(2, 1, CloseCause.NORMAL_FINISH)));
        assertFalse(state.accept(new Close(epoch, 1, 1, EnumHand.OFF_HAND, spell, CloseCause.NORMAL_FINISH, 17)));
        assertFalse(state.ready());
        assertTrue(state.accept(close(1, 1, CloseCause.NORMAL_NO_FINISH)));
        assertFalse(state.accept(close(1, 2, CloseCause.NORMAL_FINISH)));
        assertFalse(state.finishPermitted());
    }
    @Test void clientTimeoutCannotBeRestartedOrRecoveredByAnEpochPacket() {
        ClientUseState state = client();
        state.release(); state.capture(EnumHand.MAIN_HAND, spell);
        assertFalse(state.elapse(ClientUseState.CONFIRMATION_NANOS - 1));
        assertEquals(0, state.input(EnumHand.MAIN_HAND, spell));
        state.capability(epoch);
        assertTrue(state.elapse(1));
        assertTrue(state.faulted()); assertFalse(state.pending());
        assertFalse(state.accept(close(1, 1, CloseCause.NORMAL_FINISH)));
        state.capability(UUID.randomUUID());
        assertEquals(epoch, state.epoch());
        assertEquals(0, state.input(EnumHand.MAIN_HAND, spell));
        assertFalse(state.finishPermitted());
    }
    @Test void finishContinuationRetiresBeforeReentrantObserversAndPreservesOrder() {
        List<String> calls = new ArrayList<>();
        FinishContinuation continuation = new FinishContinuation(() -> true);
        continuation.capture(() -> {
            calls.add("event");
            assertEquals(FinishContinuation.Result.ALREADY_RESOLVED, continuation.resolve(true));
        });
        continuation.capture(() -> calls.add("body"));
        continuation.capture(() -> calls.add("cooldown"));
        assertEquals(FinishContinuation.Result.FINISHED, continuation.resolve(true));
        assertEquals(Arrays.asList("event", "body", "cooldown"), calls);
        assertEquals(FinishContinuation.Result.ALREADY_RESOLVED, continuation.resolve(true));
    }
    @Test void duplicatePermissionCannotRepeatCapturedFinishOperations() {
        AtomicInteger calls = new AtomicInteger();
        FinishContinuation continuation = new FinishContinuation(() -> true);
        continuation.capture(calls::incrementAndGet);
        continuation.capture(calls::incrementAndGet);
        continuation.capture(calls::incrementAndGet);
        assertEquals(FinishContinuation.Result.FINISHED, continuation.resolve(true));
        FinishContinuation.Result duplicate = continuation.resolve(true);
        assertEquals(3, calls.get(), "duplicate permission must not execute any retained finish action again");
        assertEquals(FinishContinuation.Result.ALREADY_RESOLVED, duplicate);
    }
    @Test void cancelledOrSuppressedContinuationCannotCallOrMigrateActions() {
        AtomicInteger calls = new AtomicInteger();
        for (boolean permitted : new boolean[] { false, true }) {
            FinishContinuation continuation = new FinishContinuation(() -> false);
            continuation.capture(calls::incrementAndGet);
            assertEquals(permitted ? FinishContinuation.Result.CANCELLED : FinishContinuation.Result.SUPPRESSED,
                    continuation.resolve(permitted));
            assertEquals(FinishContinuation.Result.ALREADY_RESOLVED, continuation.resolve(true));
        }
        assertEquals(0, calls.get());
    }
    @Test void continuationFailureCannotEnableASecondInvocation() {
        FinishContinuation continuation = new FinishContinuation(() -> true);
        continuation.capture(() -> { throw new IllegalStateException("broken event"); });
        assertThrows(IllegalStateException.class, () -> continuation.resolve(true));
        assertEquals(FinishContinuation.Result.ALREADY_RESOLVED, continuation.resolve(true));
        assertThrows(IllegalStateException.class, () -> continuation.capture(() -> {}));
    }
    @Test void finishObserverCannotMigrateRemainingOperationsToAReplacementContext() {
        boolean[] valid = { true };
        AtomicInteger body = new AtomicInteger();
        FinishContinuation continuation = new FinishContinuation(() -> valid[0]);
        continuation.capture(() -> valid[0] = false);
        continuation.capture(body::incrementAndGet);
        assertEquals(FinishContinuation.Result.CANCELLED, continuation.resolve(true));
        assertEquals(0, body.get());
    }
    @Test void realNewGestureInsideFinishCallbackRevokesRemainingOldOperations() {
        ClientUseState state = client();
        state.release(); state.capture(EnumHand.MAIN_HAND, spell);
        assertTrue(state.accept(close(1, 7, CloseCause.NORMAL_FINISH)));
        long originalIntent = state.intent(), originalUse = state.use();
        FinishContinuation continuation = new FinishContinuation(() -> state.ownsFinish(originalIntent, originalUse));
        List<String> calls = new ArrayList<>();
        continuation.capture(() -> {
            calls.add("event");
            assertFalse(state.pending());
            assertEquals(2, state.input(EnumHand.MAIN_HAND, spell));
            state.bind(EnumHand.MAIN_HAND, spell);
        });
        continuation.capture(() -> calls.add("body"));
        continuation.capture(() -> calls.add("cooldown"));
        state.resolved();
        assertEquals(FinishContinuation.Result.CANCELLED, continuation.resolve(true));
        assertEquals(Arrays.asList("event"), calls);
        assertEquals(2, state.intent()); assertTrue(state.bound());
    }
    @Test void publicationAssociationCannotChangeKnownUseOrRecoverStructuralFault() {
        ClientUseState state = client();
        assertFalse(state.associateUse(UUID.randomUUID(), 1, 7));
        assertFalse(state.associateUse(epoch, 2, 7));
        assertFalse(state.associateUse(epoch, 1, 0));
        assertTrue(state.associateUse(epoch, 1, 7));
        assertFalse(state.associateUse(epoch, 1, 8));
        assertFalse(state.accept(close(1, 8, CloseCause.NORMAL_FINISH)));
        assertTrue(state.accept(close(1, 7, CloseCause.NORMAL_FINISH)));
        state.protocolFault(); state.capability(UUID.randomUUID());
        assertFalse(state.associateUse(epoch, 1, 7));
        assertFalse(state.ownsFinish(1, 7)); assertTrue(state.blocked());
    }
    @Test void productionCloseCodecRoundTripsEveryDispositionAndRejectsMalformedInput() {
        for (CloseCause cause : CloseCause.values()) {
            ByteBuf data = Unpooled.buffer();
            try {
                close(4, 11, cause).toBytes(data);
                Close decoded = new Close();
                ByteBuf copy = data.copy();
                try { decoded.fromBytes(copy); } finally { copy.release(); }
                assertTrue(decoded.isValid()); assertEquals(cause, decoded.cause());
                assertEquals(epoch, decoded.epoch()); assertEquals(4, decoded.intent());
                assertEquals(11, decoded.use()); assertEquals(spell, decoded.spell());
                assertEquals(17, decoded.generation());
                for (int length = 0; length < data.readableBytes(); length++) {
                    ByteBuf shortData = data.copy(0, length);
                    try { Close shortMessage = new Close(); shortMessage.fromBytes(shortData); assertFalse(shortMessage.isValid()); }
                    finally { shortData.release(); }
                }
                ByteBuf version = data.copy();
                try { version.setByte(0, 1); Close bad = new Close(); bad.fromBytes(version); assertFalse(bad.isValid()); }
                finally { version.release(); }
                ByteBuf trailing = data.copy(); trailing.writeByte(0);
                try { Close bad = new Close(); bad.fromBytes(trailing); assertFalse(bad.isValid()); }
                finally { trailing.release(); }
            } finally { data.release(); }
        }
    }
    @Test void productionIntentAndReleaseCodecEnforceBoundsAndIdentity() {
        ByteBuf data = Unpooled.buffer();
        try {
            new Intent(epoch, 3, EnumHand.OFF_HAND, spell).toBytes(data);
            Intent decoded = new Intent();
            ByteBuf copy = data.copy();
            try { decoded.fromBytes(copy); } finally { copy.release(); }
            assertTrue(decoded.isValid()); assertEquals(3, decoded.intent()); assertEquals(EnumHand.OFF_HAND, decoded.hand());
            ByteBuf oversized = data.copy(); oversized.setShort(26, 257);
            try { Intent bad = new Intent(); bad.fromBytes(oversized); assertFalse(bad.isValid()); }
            finally { oversized.release(); }
            ByteBuf negative = data.copy(); negative.setLong(17, -1);
            try { Intent bad = new Intent(); bad.fromBytes(negative); assertFalse(bad.isValid()); }
            finally { negative.release(); }
            data.clear(); new Release(epoch, 3).toBytes(data);
            Release release = new Release(); release.fromBytes(data); assertTrue(release.isValid());
            assertEquals(epoch, release.epoch()); assertEquals(3, release.intent());
            assertThrows(IllegalArgumentException.class, () -> new Release(epoch, 0));
        } finally { data.release(); }
    }
}
