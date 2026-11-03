package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.api.*;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CastCostCoordinatorTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    private CastAttemptContext context() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        return new CastAttemptContext(player, mock(Spell.class), new ResourceLocation("test:spell"),
                new ItemStack(Items.STICK), EnumHand.MAIN_HAND, false, false, 0,
                "run/use/1", "run/attempt/1", new CastAttemptContext.AcceptedCost(
                        12, 12, 1, 1, 0, 100), 12, 12, 0);
    }

    @Test void registryOrdersFullNamespacedIdsAndRejectsMutationsAtomically() {
        CastCostRegistry registry = new CastCostRegistry();
        ICastCostParticipant participant = context -> CastCostPreparation.notApplicable();
        registry.register(new ResourceLocation("z", "a"), participant);
        registry.register(new ResourceLocation("a", "z"), participant);
        assertEquals("a:z", registry.snapshot().get(0).id.toString());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new ResourceLocation("z", "a"), participant));
        assertThrows(NullPointerException.class, () -> registry.register(null, participant));
        assertEquals(2, registry.snapshot().size());
        registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registry.register(new ResourceLocation("b:b"), participant));
        assertEquals(2, registry.snapshot().size());
    }

    @Test void trueEffectSettlesLocalThenIdOrderBeforeFinalNotificationAndAcceptedCompletion() {
        List<String> trace = new ArrayList<>();
        Local local = new Local(trace);
        CastCostRegistry registry = new CastCostRegistry();
        Handle b = new Handle("b", trace), a = new Handle("a", trace);
        registry.register(new ResourceLocation("test:b"), participant(b, trace));
        registry.register(new ResourceLocation("test:a"), participant(a, trace));
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), local, () -> {
            assertFalse(CastCostCoordinator.isCallbackActive());
            trace.add("effect"); return true;
        });
        assertTrue(result.isAccepted());
        assertEquals(Arrays.asList("prepare:a", "prepare:b", "effect", "local", "settle:a",
                "settle:b", "notify:a:ACCEPTED", "notify:b:ACCEPTED", "accepted"), trace);
        assertEquals(12, result.getPaidMana());
        assertEquals(4.0D, result.getParticipants().get(0).getAmount());
        assertEquals(0, a.releases + b.releases);
        assertFalse(CastCostCoordinator.isCallbackActive());
    }

    @Test void finalObserverInvalidationPreservesPaymentButSuppressesSuccess() {
        assertFinalObserverFailure(false, CastAttemptOutcome.Status.INVALID_AFTER_EFFECT);
    }

    @Test void finalObserverThrowPreservesPaymentButSuppressesSuccess() {
        assertFinalObserverFailure(true, CastAttemptOutcome.Status.SETTLEMENT_FAULT);
    }

    private void assertFinalObserverFailure(boolean throwsFault, CastAttemptOutcome.Status status) {
        List<String> trace = new ArrayList<>();
        Local local = new Local(trace);
        CastCostRegistry registry = new CastCostRegistry();
        Handle handle = new Handle("a", trace);
        registry.register(new ResourceLocation("test:a"), new ICastCostParticipant() {
            public CastCostPreparation prepare(CastAttemptContext context) {
                return CastCostPreparation.accepted(handle);
            }

            public void onAttemptResolved(CastAttemptOutcome outcome) {
                assertEquals(12, outcome.getPaidMana());
                assertTrue(CastCostCoordinator.isCallbackActive());
                if (throwsFault) throw new IllegalStateException("Observer failed");
                local.valid = false;
            }
        });
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), local, () -> true);
        assertEquals(status, result.getStatus());
        assertEquals(12, result.getPaidMana());
        assertEquals(4.0D, result.getParticipants().get(0).getAmount());
        assertFalse(local.accepted);
        assertEquals(0, handle.releases);
        assertFalse(CastCostCoordinator.isCallbackActive());
    }

    @Test void laterRefusalReleasesEarlierClaimsInReverseWithoutEffectOrPayment() {
        List<String> trace = new ArrayList<>();
        Local local = new Local(trace);
        CastCostRegistry registry = new CastCostRegistry();
        Handle a = new Handle("a", trace), b = new Handle("b", trace);
        registry.register(new ResourceLocation("test:a"), participant(a, trace));
        registry.register(new ResourceLocation("test:b"), participant(b, trace));
        registry.register(new ResourceLocation("test:c"), c -> CastCostPreparation.refused("empty"));
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), local,
                () -> { fail("Refused effect executed"); return true; });
        assertEquals(CastAttemptOutcome.Status.REFUSED, result.getStatus());
        assertEquals(0, result.getPaidMana());
        assertTrue(trace.indexOf("release:b") < trace.indexOf("release:a"));
        assertEquals(1, a.releases); assertEquals(1, b.releases);
        assertFalse(local.committed); assertFalse(local.accepted);
    }

    @Test void falseEffectReleasesAndNeverStartsSuccessState() {
        List<String> trace = new ArrayList<>();
        Local local = new Local(trace);
        Handle handle = new Handle("a", trace);
        CastAttemptOutcome result = CastCostCoordinator.execute(registry(handle, trace), context(), local, () -> false);
        assertEquals(CastAttemptOutcome.Status.FALSE_EFFECT, result.getStatus());
        assertEquals(1, handle.releases);
        assertFalse(local.committed); assertFalse(local.accepted);
    }

    @Test void effectThrowableIsPreservedDespiteFailingCleanupAndAllClaimsClose() {
        List<String> trace = new ArrayList<>();
        Handle a = new Handle("a", trace), b = new Handle("b", trace);
        b.releaseFailure = true;
        CastCostRegistry registry = registry(a, trace);
        registry.register(new ResourceLocation("test:b"), participant(b, trace));
        RuntimeException original = new RuntimeException("effect");
        Local local = new Local(trace);
        assertSame(original, assertThrows(RuntimeException.class, () -> CastCostCoordinator.execute(
                registry, context(), local, () -> { throw original; })));
        assertEquals(1, a.releases); assertEquals(1, b.releases);
        assertFalse(local.committed); assertTrue(local.released);
    }

    @Test void prepareExceptionAndNullAreFaultsNotLegacyFallback() {
        for (boolean returnsNull : new boolean[] { true, false }) {
            CastCostRegistry registry = new CastCostRegistry();
            registry.register(new ResourceLocation("test:a"), c -> {
                if (returnsNull) return null;
                throw new IllegalStateException("prepare");
            });
            CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), new Local(new ArrayList<>()),
                    () -> { fail("Fault became a legacy effect"); return true; });
            assertEquals(CastAttemptOutcome.Status.REFUSED, result.getStatus());
            assertEquals(CastParticipantOutcome.Failure.PREPARATION, result.getParticipants().get(0).getFailure());
        }
    }

    @Test void notApplicableClosesTemporaryLocalClaimAndSelectsUnexecutedLegacyPath() {
        CastCostRegistry registry = new CastCostRegistry();
        registry.register(new ResourceLocation("test:a"), c -> CastCostPreparation.notApplicable());
        Local local = new Local(new ArrayList<>());
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), local,
                () -> { fail("Legacy selection executed coordinated effect"); return true; });
        assertEquals(CastAttemptOutcome.Status.LEGACY, result.getStatus());
        assertTrue(local.released); assertFalse(local.committed);
        assertFalse(result.wasEffectInvoked());
    }

    @Test void invalidBeforeAndAfterEffectAreDifferentAndUnpaid() {
        for (boolean before : new boolean[] { true, false }) {
            List<String> trace = new ArrayList<>();
            Handle handle = new Handle("a", trace);
            handle.valid = !before;
            AtomicInteger effects = new AtomicInteger();
            CastAttemptOutcome result = CastCostCoordinator.execute(registry(handle, trace), context(),
                    new Local(trace), () -> { effects.incrementAndGet(); handle.valid = false; return true; });
            assertEquals(before ? CastAttemptOutcome.Status.INVALID_BEFORE_EFFECT
                    : CastAttemptOutcome.Status.INVALID_AFTER_EFFECT, result.getStatus());
            assertEquals(before ? 0 : 1, effects.get());
            assertEquals(0, result.getPaidMana()); assertEquals(1, handle.releases);
        }
    }

    @Test void laterUnknownDebitPreservesEarlierActualPaymentsAndSkipsSuccess() {
        List<String> trace = new ArrayList<>();
        Handle a = new Handle("a", trace), b = new Handle("b", trace);
        b.settleFailure = true;
        CastCostRegistry registry = registry(a, trace);
        registry.register(new ResourceLocation("test:b"), participant(b, trace));
        Local local = new Local(trace);
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), local, () -> true);
        assertEquals(CastAttemptOutcome.Status.SETTLEMENT_FAULT, result.getStatus());
        assertEquals(12, result.getPaidMana()); assertFalse(local.accepted);
        assertEquals(CastParticipantOutcome.Status.PAID, result.getParticipants().get(0).getStatus());
        assertFalse(result.getParticipants().get(1).isAmountKnown());
        assertTrue(Double.isNaN(result.getParticipants().get(1).getAmount()));
        assertEquals(0, a.releases); assertEquals(1, b.releases);
    }

    @Test void allCallbacksIncludingLocalObserversAndFinalNotificationsGuardNestedCasting() {
        List<String> trace = new ArrayList<>();
        Handle handle = new Handle("a", trace);
        Local local = new Local(trace) {
            @Override public void commit() {
                assertTrue(CastCostCoordinator.isCallbackActive());
                assertFalse(WizardryWandBridge.isCastAllowed());
                super.commit();
            }
        };
        assertTrue(CastCostCoordinator.execute(registry(handle, trace), context(), local, () -> true).isAccepted());
    }

    @Test void finalNotificationFailureCannotRewritePaidReceipt() {
        List<String> trace = new ArrayList<>();
        Handle handle = new Handle("a", trace);
        CastCostRegistry registry = new CastCostRegistry();
        registry.register(new ResourceLocation("test:a"), new ICastCostParticipant() {
            public CastCostPreparation prepare(CastAttemptContext c) { return CastCostPreparation.accepted(handle); }
            public void onAttemptResolved(CastAttemptOutcome result) { throw new IllegalStateException("observer"); }
        });
        CastAttemptOutcome result = CastCostCoordinator.execute(registry, context(), new Local(trace), () -> true);
        assertEquals(CastAttemptOutcome.Status.SETTLEMENT_FAULT, result.getStatus());
        assertEquals(CastParticipantOutcome.Status.PAID, result.getParticipants().get(0).getStatus());
        assertEquals(CastParticipantOutcome.Failure.NOTIFICATION, result.getParticipants().get(0).getFailure());
    }

    @Test void nestedReuseCannotReleaseTheParentsActiveHandle() {
        List<String> trace = new ArrayList<>();
        Handle handle = new Handle("a", trace);
        CastCostRegistry registry = registry(handle, trace);
        CastAttemptOutcome outer = CastCostCoordinator.execute(registry, context(), new Local(trace), () -> {
            CastAttemptOutcome inner = CastCostCoordinator.execute(registry, context(), new Local(trace),
                    () -> { fail("Reused active handle executed nested effect"); return true; });
            assertEquals(CastParticipantOutcome.Failure.HANDLE_REUSED,
                    inner.getParticipants().get(0).getFailure());
            assertEquals(0, handle.releases);
            return true;
        });
        assertTrue(outer.isAccepted());
        assertEquals(0, handle.releases);
    }

    @Test void localObserverInvalidationIsCheckedBeforeAnyProviderDebit() {
        List<String> trace = new ArrayList<>();
        Handle handle = new Handle("a", trace);
        Local local = new Local(trace) {
            @Override public void commit() { super.commit(); handle.valid = false; }
        };
        CastAttemptOutcome result = CastCostCoordinator.execute(registry(handle, trace), context(), local, () -> true);
        assertEquals(CastAttemptOutcome.Status.INVALID_AFTER_EFFECT, result.getStatus());
        assertEquals(12, result.getPaidMana());
        assertEquals(1, handle.releases);
        assertFalse(trace.contains("settle:a"));
        assertFalse(local.accepted);
    }

    private CastCostRegistry registry(Handle handle, List<String> trace) {
        CastCostRegistry registry = new CastCostRegistry();
        registry.register(new ResourceLocation("test:a"), participant(handle, trace));
        return registry;
    }
    private ICastCostParticipant participant(Handle handle, List<String> trace) {
        return new ICastCostParticipant() {
            public CastCostPreparation prepare(CastAttemptContext c) {
                assertTrue(CastCostCoordinator.isCallbackActive());
                trace.add("prepare:" + handle.id); return CastCostPreparation.accepted(handle);
            }
            public void onAttemptResolved(CastAttemptOutcome result) {
                assertTrue(CastCostCoordinator.isCallbackActive());
                trace.add("notify:" + handle.id + ":" + result.getStatus());
            }
        };
    }
    private static class Local implements CastCostCoordinator.LocalPayment {
        final List<String> trace;
        boolean committed, accepted, released;
        boolean valid = true;
        Local(List<String> trace) { this.trace = trace; }
        public boolean isValid() { return valid; }
        public void commit() { committed = true; trace.add("local"); }
        public void completeAccepted() { accepted = true; trace.add("accepted"); }
        public void release() { released = true; }
        public boolean isCommitted() { return committed; }
        public int paidMana() { return committed ? 12 : 0; }
        public int paidHunger() { return 0; }
    }
    private static final class Handle implements ICastCostReservation {
        final String id;
        final List<String> trace;
        boolean valid = true, releaseFailure, settleFailure;
        int releases;
        Handle(String id, List<String> trace) { this.id = id; this.trace = trace; }
        public boolean isValid() { assertTrue(CastCostCoordinator.isCallbackActive()); return valid; }
        public CastCostSettlement settle() {
            assertTrue(CastCostCoordinator.isCallbackActive());
            trace.add("settle:" + id);
            if (settleFailure) throw new IllegalStateException("Indeterminate provider debit");
            return CastCostSettlement.paid(4.0D, false);
        }
        public void release(CastCostReleaseReason reason) {
            assertTrue(CastCostCoordinator.isCallbackActive());
            releases++; trace.add("release:" + id);
            if (releaseFailure) throw new IllegalStateException("release");
        }
    }
}
