package com.mahghuuuls.jawms.testfixture.automation;

/** Fixture-only live acknowledgement bound. Delayed traffic never renews the original deadline. */
public final class PilotLease {
    public static final long FRESH_NANOS = 2_000_000_000L;
    private final long started;
    private long remaining = PilotSequence.LIMIT_NANOS;
    private long lastTick, lastAck;
    private boolean acknowledged;
    public PilotLease(long started) { this.started = started; lastTick = started; }
    private void elapse(long now) {
        if (now < lastTick) { remaining = 0; return; }
        remaining = Math.max(0, remaining - (now - lastTick)); lastTick = now;
    }
    public boolean acknowledge(long sent, long received, long serverRemaining) {
        elapse(received);
        if (sent < started || received < sent || serverRemaining <= received - sent || received - sent >= FRESH_NANOS
                || acknowledged && received - lastAck >= FRESH_NANOS) { remaining = 0; return false; }
        remaining = Math.min(remaining, serverRemaining - (received - sent));
        lastAck = received; acknowledged = true; return remaining > 0;
    }
    public boolean authorized(long now) {
        elapse(now); return acknowledged && remaining > 0 && now - lastAck < FRESH_NANOS;
    }
    public boolean expired(long now) { elapse(now); return remaining == 0 || acknowledged && now - lastAck >= FRESH_NANOS; }
}
