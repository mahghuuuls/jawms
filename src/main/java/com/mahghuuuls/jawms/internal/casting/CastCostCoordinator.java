package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.api.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.util.ResourceLocation;

/** Owns the synchronous protocol; payment owners never receive an effect callback. */
final class CastCostCoordinator {
    private static final ThreadLocal<Integer> CALLBACK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Set<ICastCostReservation>> OPEN_HANDLES =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /** Local authority finalizes its receipt before publishing any observer. */
    interface LocalPayment {
        boolean isValid();
        void commit();
        void completeAccepted();
        void release();
        boolean isCommitted();
        int paidMana();
        int paidHunger();
        default boolean isAmountKnown() { return true; }
    }

    static boolean isCallbackActive() { return CALLBACK_DEPTH.get() > 0; }

    static CastAttemptOutcome execute(CastCostRegistry registry, CastAttemptContext context,
            LocalPayment local, BooleanSupplier effect) {
        return execute(registry, context, local, effect, outcome -> {}, (outcome, participant) -> {});
    }

    static CastAttemptOutcome execute(CastCostRegistry registry, CastAttemptContext context,
            LocalPayment local, BooleanSupplier effect,
            Consumer<CastAttemptOutcome> reportThrownEffect,
            BiConsumer<CastAttemptOutcome, ResourceLocation> reportAdditionalNotificationFault) {
        Attempt attempt = new Attempt(context, local, reportAdditionalNotificationFault);
        try {
            for (CastCostRegistry.Entry registration : registry.snapshot()) {
                Claim claim = new Claim(registration);
                attempt.claims.add(claim);
                CastCostPreparation preparation;
                try {
                    preparation = callback(() -> registration.participant.prepare(context));
                    if (preparation == null) throw new IllegalStateException("Null preparation");
                } catch (RuntimeException fault) {
                    claim.fail(CastParticipantOutcome.Failure.PREPARATION, false);
                    attempt.status = CastAttemptOutcome.Status.REFUSED;
                    return attempt.finish(CastCostReleaseReason.PREPARATION_REJECTED);
                }
                if (preparation.getStatus() == CastCostPreparation.Status.NOT_APPLICABLE) continue;
                attempt.applicable = true;
                if (preparation.getStatus() == CastCostPreparation.Status.REFUSED) {
                    claim.status = CastParticipantOutcome.Status.REFUSED;
                    claim.category = preparation.getCategory();
                    attempt.status = CastAttemptOutcome.Status.REFUSED;
                    return attempt.finish(CastCostReleaseReason.PREPARATION_REJECTED);
                }
                ICastCostReservation handle = preparation.getReservation();
                if (!OPEN_HANDLES.get().add(handle)) {
                    // The other attempt owns it; never release a borrowed active handle.
                    claim.fail(CastParticipantOutcome.Failure.HANDLE_REUSED, false);
                    attempt.status = CastAttemptOutcome.Status.REFUSED;
                    return attempt.finish(CastCostReleaseReason.PREPARATION_REJECTED);
                }
                claim.handle = handle;
                claim.open = true;
            }
            if (!attempt.applicable) {
                attempt.status = CastAttemptOutcome.Status.LEGACY;
                return attempt.finish(CastCostReleaseReason.ABANDONED_CALL);
            }
            if (!attempt.valid()) {
                attempt.status = CastAttemptOutcome.Status.INVALID_BEFORE_EFFECT;
                return attempt.finish(CastCostReleaseReason.INVALID_BEFORE_EFFECT);
            }
            attempt.effectInvoked = true;
            try {
                attempt.effectSuccessful = effect.getAsBoolean();
            } catch (RuntimeException | Error original) {
                attempt.status = CastAttemptOutcome.Status.EFFECT_EXCEPTION;
                try { attempt.finish(CastCostReleaseReason.EFFECT_EXCEPTION); }
                catch (RuntimeException | Error cleanupFault) { if (cleanupFault != original) original.addSuppressed(cleanupFault); }
                try { reportThrownEffect.accept(attempt.receipt()); }
                catch (RuntimeException | Error diagnosticFault) {
                    if (diagnosticFault != original) original.addSuppressed(diagnosticFault);
                }
                throw original;
            }
            if (!attempt.effectSuccessful) {
                attempt.status = CastAttemptOutcome.Status.FALSE_EFFECT;
                return attempt.finish(CastCostReleaseReason.FALSE_EFFECT);
            }
            if (!attempt.valid()) {
                attempt.status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                return attempt.finish(CastCostReleaseReason.INVALID_AFTER_EFFECT);
            }
            try {
                callback(() -> { local.commit(); return null; });
                for (Claim claim : attempt.claims) {
                    if (!attempt.valid()) {
                        attempt.status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                        return attempt.finish(CastCostReleaseReason.INVALID_AFTER_EFFECT);
                    }
                    if (!claim.open) continue;
                    CastCostSettlement settlement;
                    try {
                        settlement = callback(claim.handle::settle);
                        if (settlement == null) throw new IllegalStateException("Null settlement");
                    } catch (RuntimeException fault) {
                        claim.fail(CastParticipantOutcome.Failure.SETTLEMENT, true);
                        throw fault;
                    }
                    if (settlement.getStatus() == CastCostSettlement.Status.INVALIDATED) {
                        claim.status = CastParticipantOutcome.Status.INVALIDATED;
                        claim.amountKnown = false;
                        attempt.status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                        return attempt.finish(CastCostReleaseReason.INVALID_AFTER_EFFECT);
                    }
                    claim.close();
                    claim.status = settlement.getStatus() == CastCostSettlement.Status.FREE
                            ? CastParticipantOutcome.Status.FREE : CastParticipantOutcome.Status.PAID;
                    claim.amountKnown = true;
                    claim.amount = settlement.getAmount();
                    claim.stop = settlement.mustStopAfterSuccess();
                }
                if (!attempt.valid()) {
                    attempt.status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                    return attempt.finish(CastCostReleaseReason.INVALID_AFTER_EFFECT);
                }
                attempt.status = CastAttemptOutcome.Status.ACCEPTED;
            } catch (RuntimeException fault) {
                attempt.status = CastAttemptOutcome.Status.SETTLEMENT_FAULT;
            }
            return attempt.finish(CastCostReleaseReason.EARLIER_SETTLEMENT_FAILURE);
        } catch (RuntimeException | Error original) {
            try { attempt.cleanup(CastCostReleaseReason.ABANDONED_CALL); }
            catch (RuntimeException | Error cleanupFault) { original.addSuppressed(cleanupFault); }
            throw original;
        } finally {
            // Also covers Errors outside the effect; never strand later handles.
            attempt.cleanup(CastCostReleaseReason.ABANDONED_CALL);
            if (OPEN_HANDLES.get().isEmpty()) OPEN_HANDLES.remove();
        }
    }

    static void runGuarded(Runnable action) {
        callback(() -> { action.run(); return null; });
    }

    private static <T> T callback(java.util.function.Supplier<T> action) {
        int previous = CALLBACK_DEPTH.get();
        CALLBACK_DEPTH.set(previous + 1);
        try { return action.get(); }
        finally {
            if (previous == 0) CALLBACK_DEPTH.remove(); else CALLBACK_DEPTH.set(previous);
        }
    }

    private static final class Attempt {
        final CastAttemptContext context;
        final LocalPayment local;
        final List<Claim> claims = new ArrayList<>();
        CastAttemptOutcome.Status status = CastAttemptOutcome.Status.REFUSED;
        boolean applicable, effectInvoked, effectSuccessful, cleaned, notified;
        private final BiConsumer<CastAttemptOutcome, ResourceLocation> reportAdditionalNotificationFault;

        Attempt(CastAttemptContext context, LocalPayment local,
                BiConsumer<CastAttemptOutcome, ResourceLocation> reportAdditionalNotificationFault) {
            this.reportAdditionalNotificationFault = reportAdditionalNotificationFault;
            this.context = context;
            this.local = local;
        }
        boolean valid() {
            if (!local.isValid()) return false;
            for (Claim claim : claims) {
                if (!claim.open) continue;
                try {
                    if (!callback(claim.handle::isValid)) {
                        claim.status = CastParticipantOutcome.Status.INVALIDATED;
                        return false;
                    }
                } catch (RuntimeException fault) {
                    claim.fail(CastParticipantOutcome.Failure.VALIDATION, false);
                    return false;
                }
            }
            // Validation callbacks are arbitrary addon code; recheck our own binding last.
            return local.isValid();
        }
        void cleanup(CastCostReleaseReason reason) {
            if (cleaned) return;
            cleaned = true;
            Error fatal = null;
            for (int i = claims.size() - 1; i >= 0; i--) {
                Claim claim = claims.get(i);
                if (!claim.open) continue;
                try {
                    callback(() -> { claim.handle.release(reason); return null; });
                    if (claim.status == CastParticipantOutcome.Status.NOT_APPLICABLE) {
                        claim.status = CastParticipantOutcome.Status.RELEASED;
                    }
                } catch (RuntimeException fault) {
                    claim.fail(CastParticipantOutcome.Failure.CLEANUP, true);
                    if (!effectSuccessful) status = CastAttemptOutcome.Status.CLEANUP_FAULT;
                } catch (Error error) {
                    if (fatal == null) fatal = error; else fatal.addSuppressed(error);
                } finally { claim.close(); }
            }
            try { local.release(); }
            catch (RuntimeException fault) { status = CastAttemptOutcome.Status.CLEANUP_FAULT; }
            finally { if (fatal != null) throw fatal; }
        }
        CastAttemptOutcome receipt() {
            List<CastParticipantOutcome> results = new ArrayList<>();
            for (Claim claim : claims) results.add(claim.receipt());
            return new CastAttemptOutcome(context, status, effectInvoked, effectSuccessful,
                    local.isAmountKnown(), local.paidMana(), local.paidHunger(), results);
        }
        CastAttemptOutcome finish(CastCostReleaseReason reason) {
            cleanup(reason);

            if (!notified) {
                notified = true;
                for (Claim claim : claims) {
                    try {
                        final CastAttemptOutcome notification = receipt();
                        callback(() -> {
                            claim.registration.participant.onAttemptResolved(notification);
                            return null;
                        });
                    } catch (RuntimeException fault) {
                        // The actual amount/status remains intact after a notification fault.
                        if (claim.failure == CastParticipantOutcome.Failure.NONE) {
                            claim.failure = CastParticipantOutcome.Failure.NOTIFICATION;
                        } else {
                            try {
                                reportAdditionalNotificationFault.accept(receipt(), claim.registration.id);
                            } catch (RuntimeException diagnosticFault) {
                                // Diagnostic delivery cannot prevent later notifications.
                            }
                        }
                        if (status == CastAttemptOutcome.Status.ACCEPTED) {
                            status = CastAttemptOutcome.Status.SETTLEMENT_FAULT;
                        }
                    }
                    if (status == CastAttemptOutcome.Status.ACCEPTED && !local.isValid()) {
                        status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                    }
                }
            }
            if (status == CastAttemptOutcome.Status.ACCEPTED) {
                try {
                    callback(() -> { local.completeAccepted(); return null; });
                    if (!local.isValid()) status = CastAttemptOutcome.Status.INVALID_AFTER_EFFECT;
                } catch (RuntimeException fault) {
                    status = CastAttemptOutcome.Status.SETTLEMENT_FAULT;
                }
            }
            return receipt();
        }
    }

    private static final class Claim {
        final CastCostRegistry.Entry registration;
        ICastCostReservation handle;
        boolean open, stop, amountKnown = true;
        double amount;
        String category = "";
        CastParticipantOutcome.Status status = CastParticipantOutcome.Status.NOT_APPLICABLE;
        CastParticipantOutcome.Failure failure = CastParticipantOutcome.Failure.NONE;
        Claim(CastCostRegistry.Entry registration) { this.registration = registration; }
        void close() { open = false; OPEN_HANDLES.get().remove(handle); }
        void fail(CastParticipantOutcome.Failure failure, boolean amountUnknown) {
            this.status = CastParticipantOutcome.Status.FAULT;
            this.failure = failure;
            if (amountUnknown) this.amountKnown = false;
        }
        CastParticipantOutcome receipt() {
            return new CastParticipantOutcome(registration.id, status, failure,
                    amountKnown, amount, stop, category);
        }
    }
}
