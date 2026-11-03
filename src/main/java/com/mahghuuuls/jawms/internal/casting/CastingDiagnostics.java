package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.api.CastParticipantOutcome;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CastingDiagnostics {
    private static final int FAULT_WINDOW_TICKS = 200;
    private static final int MAX_FAULT_KEYS = 256;
    private static final int MAX_SUMMARY_PARTICIPANTS = 16;
    private static volatile boolean enabled = Boolean.getBoolean("jawms.debugCasting");
    private static final Map<String, FaultWindow> FAULT_WINDOWS = new LinkedHashMap<>();
    private static FaultWindow overflow;

    private CastingDiagnostics() {}

    public static boolean isEnabled() { return enabled; }

    public static void setDevelopmentEnabled(boolean value) {
        enabled = value;
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS casting diagnostics {}.", value ? "enabled" : "disabled");
    }

    static synchronized void clear() {
        long suppressed = overflow == null ? 0 : overflow.suppressed;
        for (FaultWindow window : FAULT_WINDOWS.values()) suppressed += window.suppressed;
        if (suppressed > 0) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "JAWMS casting fault windows cleared: suppressed={}.", suppressed);
        }
        FAULT_WINDOWS.clear();
        overflow = null;
        enabled = Boolean.getBoolean("jawms.debugCasting");
    }

    static void thrownEffect(CastAttemptOutcome outcome, long tick, int manaPortion, UseSummary summary) {
        warn(outcome, "jawms:local", "EFFECT_EXCEPTION", tick);
        participation(outcome, tick, manaPortion, summary);
    }

    static void notificationFault(CastAttemptOutcome outcome, ResourceLocation participant, long tick) {
        warn(outcome, participant.toString(), CastParticipantOutcome.Failure.NOTIFICATION.name(), tick);
    }

    static void participation(CastAttemptOutcome outcome, long tick, int manaPortion, UseSummary summary) {
        boolean participantFault = false;
        for (CastParticipantOutcome participant : outcome.getParticipants()) {
            if (participant.getFailure() == CastParticipantOutcome.Failure.NONE) continue;
            participantFault = true;
            warn(outcome, participant.getParticipantId().toString(), participant.getFailure().name(), tick);
        }
        switch (outcome.getStatus()) {
            case SETTLEMENT_FAULT:
            case CLEANUP_FAULT:
                if (!participantFault) warn(outcome, "jawms:local", outcome.getStatus().name(), tick);
                break;
            default:
                break;
        }
        if (!enabled || outcome.getStatus() == CastAttemptOutcome.Status.LEGACY) return;
        if (summary != null) {
            summary.record(outcome, manaPortion);
            return;
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS participation: player={}, use={}, attempt={}, spell={}, hand={}, status={}, "
                        + "mana={}, hunger={}, localAmountKnown={}, zeroPortion={}, terminal={}.",
                outcome.getPlayerId(), outcome.getUseId(), outcome.getAttemptId(), outcome.getSpellId(),
                outcome.getHand(), outcome.getStatus(), outcome.getPaidMana(), outcome.getPaidHunger(),
                outcome.isLocalAmountKnown(), manaPortion == 0, outcome.isAccepted() && outcome.mustStopAfterSuccess());
        for (CastParticipantOutcome participant : outcome.getParticipants()) {
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS participant result: attempt={}, participant={}, status={}, failure={}, amount={}, refusal={}, stopAfterSuccess={}.",
                    outcome.getAttemptId(), participant.getParticipantId(), participant.getStatus(),
                    participant.getFailure(), participant.isAmountKnown() ? participant.getAmount() : "unknown",
                    participant.getRefusalCategory(), participant.mustStopAfterSuccess());
        }
    }

    private static synchronized void warn(CastAttemptOutcome outcome, String participant, String failure, long tick) {
        String key = participant + "/" + failure;
        FaultWindow window = FAULT_WINDOWS.get(key);
        if (window != null && tick >= window.tick && tick - window.tick < FAULT_WINDOW_TICKS) {
            window.suppressed++;
            return;
        }
        if (window == null && FAULT_WINDOWS.size() == MAX_FAULT_KEYS) {
            // Keep active windows intact. Overflow keys share one conservative window.
            if (overflow != null && tick - overflow.tick < FAULT_WINDOW_TICKS) {
                overflow.suppressed++;
                return;
            }
            long retiredSuppressions = 0;
            java.util.Iterator<FaultWindow> windows = FAULT_WINDOWS.values().iterator();
            while (windows.hasNext()) {
                FaultWindow existing = windows.next();
                if (tick - existing.tick >= FAULT_WINDOW_TICKS) {
                    retiredSuppressions += existing.suppressed;
                    windows.remove();
                }
            }
            long suppressed = retiredSuppressions + (overflow == null ? 0 : overflow.suppressed);
            if (FAULT_WINDOWS.size() == MAX_FAULT_KEYS) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "JAWMS casting fault capacity: participant={}, failure={}, player={}, use={}, attempt={}, suppressed={}.",
                        participant, failure, outcome.getPlayerId(), outcome.getUseId(), outcome.getAttemptId(),
                        suppressed);
                overflow = new FaultWindow(tick);
                return;
            }
            overflow = null;
            if (suppressed > 0) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "JAWMS casting fault windows retired: suppressed={}.", suppressed);
            }
        }
        JustAnotherWizardryManaSystemMod.LOGGER.warn(
                "JAWMS participant fault: participant={}, failure={}, player={}, use={}, attempt={}, status={}, suppressed={}.",
                participant, failure, outcome.getPlayerId(), outcome.getUseId(), outcome.getAttemptId(),
                outcome.getStatus(), window == null ? 0L : window.suppressed);
        FAULT_WINDOWS.put(key, new FaultWindow(tick));
    }

    private static final class FaultWindow {
        final long tick;
        long suppressed;

        FaultWindow(long tick) { this.tick = tick; }
    }

    /** Owned by the existing physical use, never retained in a diagnostic history. */
    static final class UseSummary {
        private final Map<ResourceLocation, ParticipantSummary> participants = new LinkedHashMap<>();
        private long attempts, zeroPortions, paidMana, paidHunger, unknownLocal, omittedResults;
        private UUID player;
        private String use, lastAttempt;
        private ResourceLocation spell;
        private EnumHand hand;
        private CastAttemptOutcome.Status lastStatus;
        private CastParticipantOutcome omittedDecision;
        private String omittedDecisionAttempt;

        void record(CastAttemptOutcome outcome, int manaPortion) {
            player = outcome.getPlayerId();
            use = outcome.getUseId();
            lastAttempt = outcome.getAttemptId();
            spell = outcome.getSpellId();
            hand = outcome.getHand();
            lastStatus = outcome.getStatus();
            attempts++;
            if (manaPortion == 0) zeroPortions++;
            paidMana += outcome.getPaidMana();
            paidHunger += outcome.getPaidHunger();
            if (!outcome.isLocalAmountKnown()) unknownLocal++;
            for (CastParticipantOutcome result : outcome.getParticipants()) {
                if (result.getStatus() == CastParticipantOutcome.Status.NOT_APPLICABLE
                        && result.getFailure() == CastParticipantOutcome.Failure.NONE) continue;
                ParticipantSummary entry = participants.get(result.getParticipantId());
                if (entry == null) {
                    if (participants.size() == MAX_SUMMARY_PARTICIPANTS) {
                        omittedResults++;
                        if (result.getStatus() == CastParticipantOutcome.Status.REFUSED
                                || result.getStatus() == CastParticipantOutcome.Status.INVALIDATED
                                || (outcome.isAccepted() && result.mustStopAfterSuccess())) {
                            omittedDecision = result;
                            omittedDecisionAttempt = lastAttempt;
                        }
                        continue;
                    }
                    entry = new ParticipantSummary();
                    participants.put(result.getParticipantId(), entry);
                }
                entry.last = result;
                entry.lastAttempt = lastAttempt;
                if (!result.isAmountKnown()) entry.unknown++;
                else if (result.getStatus() == CastParticipantOutcome.Status.PAID) entry.knownPaid += result.getAmount();
                if (outcome.isAccepted() && result.mustStopAfterSuccess()) entry.terminalAttempt = lastAttempt;
            }
        }

        void emit(ParticipantUseMessages.CloseCause close) {
            if (attempts == 0) return;
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS participation use: player={}, use={}, lastAttempt={}, spell={}, hand={}, close={}, "
                            + "status={}, attempts={}, zeroPortions={}, mana={}, hunger={}, unknownLocal={}, omittedResults={}.",
                    player, use, lastAttempt, spell, hand,
                    close, lastStatus, attempts, zeroPortions, paidMana, paidHunger, unknownLocal, omittedResults);
            participants.forEach((id, entry) -> JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS participation provider: use={}, participant={}, knownPaid={}, unknownResults={}, "
                            + "lastStatus={}, lastFailure={}, refusal={}, lastAttempt={}, terminalAttempt={}.",
                    use, id, entry.knownPaid, entry.unknown, entry.last.getStatus(),
                    entry.last.getFailure(), entry.last.getRefusalCategory(), entry.lastAttempt, entry.terminalAttempt));
            if (omittedDecision != null) {
                JustAnotherWizardryManaSystemMod.LOGGER.info(
                        "JAWMS participation decision outside summary rows: use={}, attempt={}, participant={}, "
                                + "status={}, amount={}, refusal={}, stopAfterSuccess={}.",
                        use, omittedDecisionAttempt, omittedDecision.getParticipantId(), omittedDecision.getStatus(),
                        omittedDecision.isAmountKnown() ? omittedDecision.getAmount() : "unknown",
                        omittedDecision.getRefusalCategory(), omittedDecision.mustStopAfterSuccess());
            }
        }
    }

    private static final class ParticipantSummary {
        double knownPaid;
        long unknown;
        String lastAttempt, terminalAttempt;
        CastParticipantOutcome last;
    }

    static void trace(
            String event,
            EntityPlayer player,
            ResourceLocation spellId,
            EnumHand hand,
            int cost,
            int available
    ) {
        if (!enabled) {
            return;
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS cast event: event={}, player={}, spell={}, hand={}, cost={}, available={}.",
                event,
                player.getUniqueID(),
                spellId,
                hand,
                cost,
                available
        );
    }
}
