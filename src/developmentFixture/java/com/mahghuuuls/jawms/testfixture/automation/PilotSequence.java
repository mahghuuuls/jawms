package com.mahghuuuls.jawms.testfixture.automation;

/** Only schedules the use key. Completion is not an evidence verdict or qualification. */
public final class PilotSequence {
    public enum Kind {
        NORMAL(false, false), TERMINAL(true, false), DELAYED_NORMAL(false, true), DELAYED_TERMINAL(true, true);
        public final boolean terminal, delayed;
        Kind(boolean terminal, boolean delayed) { this.terminal = terminal; this.delayed = delayed; }
    }
    public enum Phase { READY, HOLD, RELEASE, RETRY, FINAL, COMPLETE, ABORTED }
    public static final long LIMIT_NANOS = 45_000_000_000L;
    private final Kind kind;
    private final long started;
    private Phase phase = Phase.READY;
    private boolean key;
    private String reason = "";
    private long releaseSample = -1;

    public PilotSequence(Kind kind, long started) { this.kind = kind; this.started = started; }
    public Phase phase() { return phase; }
    public boolean key() { return key; }
    public String reason() { return reason; }
    public void abort(String reason) { this.reason = reason; phase = Phase.ABORTED; key = false; }

    /** Inputs are observations, not permissions to clear or modify production state. */
    public void advance(long now, boolean safe, boolean ready, long sample, int effects,
            boolean pending, boolean discarded, boolean settled, boolean clean) {
        if (phase == Phase.COMPLETE || phase == Phase.ABORTED) return;
        if (!safe || now < started || now - started >= LIMIT_NANOS) {
            abort(safe ? "pilot_deadline_unreached" : "input_scope_lost"); return;
        }
        switch (phase) {
            case READY:
                if (ready) { phase = Phase.HOLD; key = true; }
                break;
            case HOLD:
                // Delayed terminal intentionally has no received close/retry latch yet.
                if (effects >= (kind.terminal ? 1 : 2) && settled
                        && (!kind.terminal || kind.delayed || discarded)) {
                    phase = Phase.RELEASE; key = false; releaseSample = sample;
                }
                break;
            case RELEASE:
                if (kind.delayed) {
                    if (pending) { phase = Phase.RETRY; key = true; }
                } else phase = Phase.FINAL;
                break;
            case RETRY:
                if (discarded) { phase = Phase.FINAL; key = false; releaseSample = sample; }
                break;
            case FINAL:
                // A later server readback is mandatory; local driver completion cannot PASS a case.
                if (sample > releaseSample && clean && settled) { phase = Phase.COMPLETE; key = false; }
                break;
            default: break;
        }
    }
}
